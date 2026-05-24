package dev.dong4j.idea.skillsjars.helper.parser;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SkillJarParser} 单元测试.
 *
 * <p>测试策略: 在临时目录里动态构造一个含 SKILL.md 的 jar, 通过 mock {@link VirtualFile} 仅暴露
 * 路径信息, 直接调用基于 {@code Path} 的解析重载, 不依赖 IDEA VFS.</p>
 *
 * @author dong4j
 */
class SkillJarParserTest {

    @TempDir
    Path tempDir;

    private SkillJarParser parser;

    @BeforeEach
    void setUp() {
        this.parser = new SkillJarParser();
    }

    @AfterEach
    void tearDown() {
        // @TempDir 会自动清理
    }

    @Test
    @DisplayName("能从 META-INF/skills 路径解析出 Skill")
    void should_parse_skill_under_meta_inf_skills() throws IOException {
        Path jarPath = this.tempDir.resolve("example.jar");
        writeJarWith(jarPath,
            new String[]{
                "META-INF/skills/dev/dong4j/code-review/SKILL.md"
            },
            new String[]{
                """
                    ---
                    name: code-review
                    description: 审查代码
                    allowed-tools: Read, Grep
                    license: MIT
                    ---
                    body
                    """
            });

        SkillJarArtifact artifact = this.parser.parse(
            jarPath,
            mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.of("dev.dong4j", "code-review", "1.0.0"),
            "Maven: dev.dong4j:code-review:1.0.0"
        );

        assertThat(artifact).isNotNull();
        assertThat(artifact.getSkills()).hasSize(1);
        SkillDescriptor skill = artifact.getSkills().get(0);
        assertThat(skill.getName()).isEqualTo("code-review");
        assertThat(skill.getDescription()).isEqualTo("审查代码");
        assertThat(skill.getAllowedTools()).containsExactly("Read", "Grep");
        assertThat(skill.getLicense()).isEqualTo("MIT");
        assertThat(skill.getJarEntryRoot()).isEqualTo("META-INF/skills/dev/dong4j/code-review/");
        assertThat(skill.getSkillMdPath()).isEqualTo("META-INF/skills/dev/dong4j/code-review/SKILL.md");
        assertThat(artifact.getCoordinate().toCoordinateString()).isEqualTo("dev.dong4j:code-review:1.0.0");
    }

    @Test
    @DisplayName("META-INF/resources/skills 下的 Skill 同样能被识别")
    void should_recognize_resources_skills_path() throws IOException {
        Path jarPath = this.tempDir.resolve("alt.jar");
        writeJarWith(jarPath,
            new String[]{"META-INF/resources/skills/foo/SKILL.md"},
            new String[]{"---\nname: foo\n---\n"});

        SkillJarArtifact artifact = this.parser.parse(
            jarPath, mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.unknown(), null);

        assertThat(artifact).isNotNull();
        assertThat(artifact.getSkills()).hasSize(1);
        assertThat(artifact.getSkills().get(0).getName()).isEqualTo("foo");
    }

    @Test
    @DisplayName("没有 frontmatter name 时退化为目录名")
    void should_use_directory_name_when_no_name_field() throws IOException {
        Path jarPath = this.tempDir.resolve("noname.jar");
        writeJarWith(jarPath,
            new String[]{"META-INF/skills/com/acme/awesome/SKILL.md"},
            new String[]{"# just markdown without frontmatter"});

        SkillJarArtifact artifact = this.parser.parse(
            jarPath, mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY, SkillCoordinate.unknown(), null);

        assertThat(artifact).isNotNull();
        assertThat(artifact.getSkills().get(0).getName()).isEqualTo("awesome");
    }

    @Test
    @DisplayName("一个 jar 内多 Skill 都应被解析")
    void should_parse_multiple_skills_in_one_jar() throws IOException {
        Path jarPath = this.tempDir.resolve("multi.jar");
        writeJarWith(jarPath,
            new String[]{
                "META-INF/skills/a/SKILL.md",
                "META-INF/skills/b/SKILL.md"
            },
            new String[]{
                "---\nname: a\n---\n",
                "---\nname: b\n---\n"
            });

        SkillJarArtifact artifact = this.parser.parse(
            jarPath, mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY, SkillCoordinate.unknown(), null);

        assertThat(artifact).isNotNull();
        assertThat(artifact.getSkills()).extracting(SkillDescriptor::getName)
            .containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("不含 SKILL.md 的 jar 返回 null")
    void should_return_null_when_no_skill_md() throws IOException {
        Path jarPath = this.tempDir.resolve("empty.jar");
        writeJarWith(jarPath,
            new String[]{"foo/bar/Baz.class"},
            new String[]{"binary"});

        SkillJarArtifact artifact = this.parser.parse(
            jarPath, mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY, SkillCoordinate.unknown(), null);

        assertThat(artifact).isNull();
    }

    @Test
    @DisplayName("不在 SkillsJars 路径下的 SKILL.md 不应被采纳")
    void should_ignore_unrelated_skill_md() throws IOException {
        Path jarPath = this.tempDir.resolve("unrelated.jar");
        writeJarWith(jarPath,
            new String[]{"docs/SKILL.md"},
            new String[]{"---\nname: ignored\n---\n"});

        SkillJarArtifact artifact = this.parser.parse(
            jarPath, mockVirtualFile(jarPath),
            SkillSourceType.MAVEN_DEPENDENCY, SkillCoordinate.unknown(), null);

        assertThat(artifact).isNull();
    }

    /**
     * 把若干 (path, content) 写入一个新 jar 文件.
     */
    private static void writeJarWith(@NotNull Path jarPath,
                                     String[] entryNames,
                                     String[] contents) throws IOException {
        File parent = jarPath.toFile().getParentFile();
        if (parent != null) {
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
