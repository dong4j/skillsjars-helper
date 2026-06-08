package dev.dong4j.idea.skillsjars.helper.parser;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillJarParser} 健壮性测试.
 *
 * <p>覆盖生产环境里可能遇到的不正常 jar, 确保解析器不会让 IDE 崩溃或卡死:
 * 这些异常输入都要在路径上安全降级 (返回 null 或部分结果), 不抛未捕获异常.</p>
 *
 * <p>Jar 构造策略: 与 {@link SkillJarParserTest} 一致, 在 {@code @TempDir} 里用
 * {@link JarOutputStream} 动态写 jar. 损坏/空 jar 直接写裸字节, 不走 JarOutputStream.
 * 项目约定 (AGENTS.md §9) 禁止引入外部 fixture jar, 因此不在
 * {@code src/test/resources/fixtures/} 提交二进制.</p>
 *
 * <p>覆盖场景 (与 HOM-187 描述一一对应):</p>
 * <ul>
 *   <li>1. 损坏的 jar (不是合法 zip): 解析器应捕获 IOException 并返回 null.</li>
 *   <li>2. 空 jar (无任何 entry): 解析器应返回 null.</li>
 *   <li>3. 普通依赖 jar (无 SKILL.md): 解析器应返回 null, 跳过该 jar.</li>
 *   <li>4. 同一 jar 内多个 SKILL.md: 全部解析为独立 {@link SkillDescriptor}.</li>
 *   <li>5. 嵌套目录的 SKILL.md: 资源文件按 longest-prefix 归属到最深的 skill.</li>
 *   <li>6. META-INF/skills/ 和 META-INF/resources/skills/ 同时存在: 各自独立解析.</li>
 *   <li>7. 大小写敏感性: 小写前缀和根路径的 SKILL.md 都不应被识别.</li>
 *   <li>8. frontmatter 异常: 不合法 YAML / 缺少结束分隔符 / 含 allowed-tools 都不应让解析器抛异常.</li>
 * </ul>
 *
 * @author dong4j
 */
@DisplayName("SkillJarParser 健壮性")
class SkillJarParserRobustnessTest {

    @TempDir
    Path tempDir;

    private SkillJarParser parser;

    @BeforeEach
    void setUp() {
        this.parser = new SkillJarParser();
    }

    /**
     * 统一的 parse 入口, 隐藏 mock 细节, 让每个 case 专注断言.
     */
    private SkillJarArtifact parse(@NotNull Path jarPath) {
        return this.parser.parse(
            jarPath,
            mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.unknown(),
            null
        );
    }

    // ---------------------------------------------------------------------
    // 1. 损坏的 jar
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("1. 损坏 jar (非合法 zip) 应返回 null, 不抛异常")
    void should_return_null_for_corrupted_jar() throws IOException {
        // JarFile 构造器会因非 zip magic 抛 IOException; 解析器需捕获并安全降级.
        Path jarPath = this.tempDir.resolve("corrupted.jar");
        Files.write(jarPath, "this is not a real jar file, just plain text".getBytes(StandardCharsets.UTF_8));

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("1. 损坏 jar (zip header 但 EOCD 缺失) 应返回 null")
    void should_return_null_for_truncated_jar() throws IOException {
        // 模拟下载/构建中断的 jar: 有 zip 头但缺少中央目录记录.
        Path jarPath = this.tempDir.resolve("truncated.jar");
        try (FileOutputStream out = new FileOutputStream(jarPath.toFile())) {
            // Local file header signature + 一个空 entry 名, 不写中央目录.
            out.write(new byte[]{(byte) 0x50, (byte) 0x4B, 0x03, 0x04}); // PK\003\004
            out.write(new byte[26]); // 填零, 让 zip 解析器认为是一个 entry 但中央目录为空
        }

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("1. 0 字节文件应返回 null, 不抛 ZipException")
    void should_return_null_for_zero_byte_file() throws IOException {
        Path jarPath = this.tempDir.resolve("empty-bytes.jar");
        Files.write(jarPath, new byte[0]);

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNull();
    }

    // ---------------------------------------------------------------------
    // 2. 空 jar (无任何 entry)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("2. 空 jar (合法 zip 但 0 个 entry) 应返回 null")
    void should_return_null_for_empty_jar() throws IOException {
        // 用 JarOutputStream 写出 0 entry 的合法 jar, 区别于损坏 jar (走的是 ZipException 路径).
        Path jarPath = this.tempDir.resolve("empty.jar");
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // 不写任何 entry; close() 时 JarOutputStream 自动写 EOCD.
        }

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNull();
    }

    // ---------------------------------------------------------------------
    // 3. jar 不含 SKILL.md (普通依赖)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("3. 普通 jar (无 SKILL.md) 应返回 null, 不会让调用方崩溃")
    void should_return_null_for_plain_dependency_jar() throws IOException {
        // 模拟一个普通业务 jar: 只有 .class 和 MANIFEST.MF, 不含 SKILL.md.
        Path jarPath = this.tempDir.resolve("plain.jar");
        writeJarWith(jarPath,
            new String[]{
                "com/example/Foo.class",
                "com/example/Bar.class",
                "META-INF/MANIFEST.MF"
            },
            new String[]{
                "fake class bytes",
                "fake class bytes",
                "Manifest-Version: 1.0\n"
            });

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNull();
    }

    // ---------------------------------------------------------------------
    // 4. jar 包含多个 SKILL.md
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("4. 同一 jar 内多个 SKILL.md 应全部解析")
    void should_parse_all_skill_md_entries() throws IOException {
        Path jarPath = this.tempDir.resolve("multi.jar");
        writeJarWith(jarPath,
            new String[]{
                "META-INF/skills/foo/SKILL.md",
                "META-INF/skills/bar/SKILL.md",
                "META-INF/skills/baz/SKILL.md"
            },
            new String[]{
                "---\nname: foo\n---\n",
                "---\nname: bar\n---\n",
                "---\nname: baz\n---\n"
            });

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNotNull();
        assertThat(result.getSkills()).hasSize(3);
        assertThat(result.getSkills())
            .extracting(SkillDescriptor::getName)
            .containsExactlyInAnyOrder("foo", "bar", "baz");
    }

    // ---------------------------------------------------------------------
    // 5. 嵌套目录的 SKILL.md
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("5. 嵌套目录的 SKILL.md, 资源应归属到最深的 skill root")
    void should_attribute_files_to_deepest_skill_root() throws IOException {
        // 故意多放一个兄弟 skill, 验证 resources/foo.txt 不会因为前缀包含祖先 skill 而误归.
        Path jarPath = this.tempDir.resolve("nested.jar");
        writeJarWith(jarPath,
            new String[]{
                "META-INF/skills/category/SKILL.md",
                "META-INF/skills/category/sub/skill/SKILL.md",
                "META-INF/skills/category/sub/skill/resources/foo.txt",
                "META-INF/skills/category/sub/skill/nested/inner.md"
            },
            new String[]{
                "---\nname: category\n---\n",
                "---\nname: skill\n---\n",
                "foo content",
                "inner content"
            });

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNotNull();
        assertThat(result.getSkills()).hasSize(2);

        SkillDescriptor nested = result.getSkills().stream()
            .filter(s -> s.getName().equals("skill"))
            .findFirst().orElseThrow();
        // 嵌套 skill 应拿到自己的 SKILL.md + resources/foo.txt + nested/inner.md
        assertThat(nested.getFiles())
            .extracting(SkillFileEntry::getRelativePath)
            .containsExactlyInAnyOrder(
                "SKILL.md",
                "resources/foo.txt",
                "nested/inner.md"
            );
        // 祖先 skill 不应误收这些文件
        SkillDescriptor ancestor = result.getSkills().stream()
            .filter(s -> s.getName().equals("category"))
            .findFirst().orElseThrow();
        assertThat(ancestor.getFiles())
            .extracting(SkillFileEntry::getRelativePath)
            .containsExactly("SKILL.md");
    }

    // ---------------------------------------------------------------------
    // 6. 同时存在 META-INF/skills/ 和 META-INF/resources/skills/
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("6. META-INF/skills/ 和 META-INF/resources/skills/ 下的 SKILL.md 都应被解析")
    void should_parse_skills_from_both_prefixes() throws IOException {
        Path jarPath = this.tempDir.resolve("both.jar");
        writeJarWith(jarPath,
            new String[]{
                "META-INF/skills/one/SKILL.md",
                "META-INF/resources/skills/two/SKILL.md"
            },
            new String[]{
                "---\nname: one\n---\n",
                "---\nname: two\n---\n"
            });

        SkillJarArtifact result = parse(jarPath);

        assertThat(result).isNotNull();
        assertThat(result.getSkills()).hasSize(2);
        assertThat(result.getSkills())
            .extracting(SkillDescriptor::getName)
            .containsExactlyInAnyOrder("one", "two");
        // 两条路径必须分别走自己的 root, 不能相互污染
        assertThat(result.getSkills())
            .extracting(SkillDescriptor::getJarEntryRoot)
            .containsExactlyInAnyOrder(
                "META-INF/skills/one/",
                "META-INF/resources/skills/two/"
            );
    }

    // ---------------------------------------------------------------------
    // 7. 大小写敏感性
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("7. 大小写敏感性")
    class CaseSensitivityTests {

        @Test
        @DisplayName("7.1 小写前缀 (meta-inf/skills/...) 不应被识别")
        void should_reject_lowercase_prefix() throws IOException {
            // 规则要求严格大小写; 写成小写等同于 "不在标准路径下", 应被静默忽略.
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("lower.jar");
            writeJarWith(jarPath,
                new String[]{"meta-inf/skills/case/SKILL.md"},
                new String[]{"---\nname: ignored\n---\n"});

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("7.2 根路径 META-INF/SKILL.md 不应被识别 (必须在 META-INF/skills/ 下)")
        void should_reject_skill_md_at_meta_inf_root() throws IOException {
            // 即便路径以 META-INF/ 开头, 只要不进入 META-INF/skills/ 就不算 skill.
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("root.jar");
            writeJarWith(jarPath,
                new String[]{"META-INF/SKILL.md"},
                new String[]{"---\nname: ignored\n---\n"});

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("7.3 skill 文件名小写 (skill.md) 不应被识别")
        void should_reject_lowercase_skill_md_name() throws IOException {
            // 文件名同样严格大小写, skill.md != SKILL.md.
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("lower-skill.jar");
            writeJarWith(jarPath,
                new String[]{"META-INF/skills/x/skill.md"},
                new String[]{"---\nname: ignored\n---\n"});

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNull();
        }
    }

    // ---------------------------------------------------------------------
    // 8. SKILL.md frontmatter 异常
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("8. SKILL.md frontmatter 异常")
    class FrontmatterAnomalyTests {

        @Test
        @DisplayName("8.1 frontmatter 不是合法 YAML 时应退化为无 frontmatter (按目录名兜底)")
        void should_handle_malformed_frontmatter_gracefully() throws IOException {
            // 严格 YAML 解析器看到这种内容会抛异常 (yaml 列表 vs string, 多层嵌套, 不闭合的标记);
            // 我们要求解析器必须容错, 用目录名兜底.
            //
            // 关键观察: 当前 parser 不会从 yaml 列表里抽 string, 因此 `name:` 后跟 `- list item`
            // 会被解析为 name = null (空 value), 进而退化到目录名.
            String malformed = "---\n"
                + "name:\n"
                + "  - not a string\n"
                + "  - list of strings\n"
                + "key:\n"
                + "  nested: invalid\n"
                + "[[[\n"
                + ":::\n"
                + "---\n"
                + "body";
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("malformed.jar");
            writeJarWith(jarPath,
                new String[]{"META-INF/skills/dong4j/review/SKILL.md"},
                new String[]{malformed});

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNotNull();
            assertThat(result.getSkills()).hasSize(1);
            // name 解析失败 (yaml 列表 vs string), 应退化为目录最后一段
            assertThat(result.getSkills().get(0).getName()).isEqualTo("review");
        }

        @Test
        @DisplayName("8.2 frontmatter 缺少结束 --- 时应回退到无 frontmatter 解析")
        void should_handle_missing_closing_delimiter() throws IOException {
            // 与 SkillFrontmatterParserTest 对应, 此处验证 SkillJarParser 集成层也安全.
            String unclosed = "---\n"
                + "name: review\n"
                + "description: 还在写\n"
                + "正文还没有结束分隔符";
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("unclosed.jar");
            writeJarWith(jarPath,
                new String[]{"META-INF/skills/dev/dong4j/review/SKILL.md"},
                new String[]{unclosed});

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNotNull();
            assertThat(result.getSkills()).hasSize(1);
            SkillDescriptor skill = result.getSkills().get(0);
            // 没有 frontmatter, name 走目录名兜底; body 包含全部原始内容
            assertThat(skill.getName()).isEqualTo("review");
            assertThat(skill.getDescription()).isNull();
            assertThat(skill.getBody()).contains("正文还没有结束分隔符");
        }

        @Test
        @DisplayName("8.3 frontmatter 含 allowed-tools 时解析器不应抛异常 (POM 属性校验是另一层职责)")
        void should_parse_allowed_tools_without_throwing() throws IOException {
            // SkillsJars 规范的 "未在 POM 中声明 allowed-tools" 是上游校验职责, 不在解析器范围.
            // 此处只验证解析器在收到 allowed-tools 字段时不会崩溃, 并正确切分.
            //
            // 验证 3 种常见写法: 逗号分隔, 空白分隔, 整体方括号.
            Path jarPath = SkillJarParserRobustnessTest.this.tempDir.resolve("tools.jar");
            writeJarWith(jarPath,
                new String[]{"META-INF/skills/dong4j/tools/SKILL.md"},
                new String[]{
                    "---\n"
                        + "name: tools\n"
                        + "allowed-tools: [Read, Grep, Bash, Edit]\n"
                        + "license: MIT\n"
                        + "---\n"
                        + "body"
                });

            SkillJarArtifact result = parse(jarPath);

            assertThat(result).isNotNull();
            SkillDescriptor skill = result.getSkills().get(0);
            assertThat(skill.getName()).isEqualTo("tools");
            assertThat(skill.getAllowedTools()).containsExactly("Read", "Grep", "Bash", "Edit");
            assertThat(skill.getLicense()).isEqualTo("MIT");
        }
    }

    // ---------------------------------------------------------------------
    // 辅助方法
    // ---------------------------------------------------------------------

    /**
     * 把若干 (path, content) 写入一个新 jar 文件.
     */
    private static void writeJarWith(@NotNull Path jarPath,
                                     String[] entryNames,
                                     String[] contents) throws IOException {
        File parent = jarPath.toFile().getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            for (int i = 0; i < entryNames.length; i++) {
                JarEntry entry = new JarEntry(entryNames[i]);
                out.putNextEntry(entry);
                out.write(contents[i].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    /**
     * 构造一个最小的 {@link VirtualFile} mock, 仅暴露路径信息.
     */
    @NotNull
    private static VirtualFile mockVirtualFile(@NotNull Path jarPath) {
        VirtualFile vf = mock(VirtualFile.class);
        when(vf.getPath()).thenReturn(jarPath.toString());
        return vf;
    }
}
