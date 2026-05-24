package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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
 * {@link SkillContentHasher} 单元测试.
 *
 * <p>该工具是 ExportPlanner 与 ExportExecutor 公共依赖, 它的稳定性直接影响 OUTDATED 判定.
 * 用真实的小 jar 验证以下关键属性:</p>
 * <ul>
 *   <li>同 skill 内容相同 → 哈希稳定 (跨多次调用).</li>
 *   <li>修改 skill 内文件内容 → 哈希变化.</li>
 *   <li>同 jar 内其他 skill 的变化不影响本 skill 的哈希 (隔离性).</li>
 *   <li>读不到 jar / 列出的 entry 缺失 → 返回 null.</li>
 * </ul>
 *
 * @author dong4j
 */
class SkillContentHasherTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("两次调用相同 jar + 同一 skill 应返回相同哈希")
    void should_be_stable_for_same_input() throws IOException {
        Path jarPath = this.tempDir.resolve("stable.jar");
        writeJar(jarPath, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\nbody"},
            {"META-INF/skills/a/examples/x.md", "example"}
        });

        SkillDescriptor skill = describe("a", "META-INF/skills/a/",
            List.of(new SkillFileEntry("SKILL.md", 0),
                new SkillFileEntry("examples/x.md", 0)));
        SkillJarArtifact artifact = artifact(jarPath, List.of(skill));

        String h1 = SkillContentHasher.hash(artifact, skill);
        String h2 = SkillContentHasher.hash(artifact, skill);

        assertThat(h1).isNotNull().hasSize(64);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("修改 skill 内文件内容应导致哈希变化")
    void should_change_when_skill_content_changes() throws IOException {
        Path v1 = this.tempDir.resolve("v1.jar");
        Path v2 = this.tempDir.resolve("v2.jar");
        writeJar(v1, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\nv1"}
        });
        writeJar(v2, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\nv2"}
        });

        SkillDescriptor skill = describe("a", "META-INF/skills/a/",
            List.of(new SkillFileEntry("SKILL.md", 0)));

        String h1 = SkillContentHasher.hash(artifact(v1, List.of(skill)), skill);
        String h2 = SkillContentHasher.hash(artifact(v2, List.of(skill)), skill);

        assertThat(h1).isNotNull();
        assertThat(h2).isNotNull();
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("同 jar 内其它 skill 内容变化不应影响本 skill 哈希")
    void should_isolate_between_skills_in_same_jar() throws IOException {
        // 同 jar 内 skill b 变化, skill a 哈希应该保持
        Path jar1 = this.tempDir.resolve("multi-v1.jar");
        Path jar2 = this.tempDir.resolve("multi-v2.jar");
        writeJar(jar1, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\nA"},
            {"META-INF/skills/b/SKILL.md", "---\nname: b\n---\nB1"}
        });
        writeJar(jar2, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\nA"},
            {"META-INF/skills/b/SKILL.md", "---\nname: b\n---\nB2"}
        });

        SkillDescriptor a = describe("a", "META-INF/skills/a/",
            List.of(new SkillFileEntry("SKILL.md", 0)));

        String h1 = SkillContentHasher.hash(artifact(jar1, List.of(a)), a);
        String h2 = SkillContentHasher.hash(artifact(jar2, List.of(a)), a);

        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("jar 不存在时返回 null")
    void should_return_null_when_jar_missing() {
        Path nonexistent = this.tempDir.resolve("nope.jar");
        SkillDescriptor skill = describe("a", "META-INF/skills/a/",
            List.of(new SkillFileEntry("SKILL.md", 0)));
        SkillJarArtifact artifact = artifact(nonexistent, List.of(skill));

        assertThat(SkillContentHasher.hash(artifact, skill)).isNull();
    }

    @Test
    @DisplayName("声明的 entry 在 jar 内不存在时返回 null")
    void should_return_null_when_declared_entry_missing() throws IOException {
        Path jarPath = this.tempDir.resolve("missing-entry.jar");
        writeJar(jarPath, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\n"}
            // 故意不写 examples/x.md
        });
        SkillDescriptor skill = describe("a", "META-INF/skills/a/",
            List.of(new SkillFileEntry("SKILL.md", 0),
                new SkillFileEntry("examples/x.md", 0))); // 声明但 jar 内缺失
        SkillJarArtifact artifact = artifact(jarPath, List.of(skill));

        assertThat(SkillContentHasher.hash(artifact, skill)).isNull();
    }

    @Test
    @DisplayName("传入的 SkillFileEntry 顺序不影响哈希结果 (内部按 relativePath 排序)")
    void should_be_independent_of_file_entry_order() throws IOException {
        Path jarPath = this.tempDir.resolve("order.jar");
        writeJar(jarPath, new String[][]{
            {"META-INF/skills/a/SKILL.md", "---\nname: a\n---\n"},
            {"META-INF/skills/a/a.md", "alpha"},
            {"META-INF/skills/a/b.md", "beta"}
        });

        SkillDescriptor order1 = describe("a", "META-INF/skills/a/",
            List.of(
                new SkillFileEntry("SKILL.md", 0),
                new SkillFileEntry("a.md", 0),
                new SkillFileEntry("b.md", 0)));
        SkillDescriptor order2 = describe("a", "META-INF/skills/a/",
            List.of(
                new SkillFileEntry("b.md", 0),
                new SkillFileEntry("SKILL.md", 0),
                new SkillFileEntry("a.md", 0)));

        String h1 = SkillContentHasher.hash(artifact(jarPath, List.of(order1)), order1);
        String h2 = SkillContentHasher.hash(artifact(jarPath, List.of(order2)), order2);

        assertThat(h1).isEqualTo(h2);
    }

    // -- helpers --------------------------------------------------------------------------------

    private static void writeJar(@NotNull Path jarPath, @NotNull String[][] entries) throws IOException {
        File parent = jarPath.toFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            for (String[] e : entries) {
                out.putNextEntry(new JarEntry(e[0]));
                out.write(e[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    @NotNull
    private static SkillDescriptor describe(@NotNull String name,
                                            @NotNull String jarEntryRoot,
                                            @NotNull List<SkillFileEntry> files) {
        return new SkillDescriptor(
            name,
            "desc",
            List.of(),
            null,
            jarEntryRoot,
            jarEntryRoot + "SKILL.md",
            "",
            files
        );
    }

    @NotNull
    private static SkillJarArtifact artifact(@NotNull Path jarPath,
                                             @NotNull List<SkillDescriptor> skills) {
        VirtualFile vf = mock(VirtualFile.class);
        when(vf.getPath()).thenReturn(jarPath.toString());
        return new SkillJarArtifact(
            vf,
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.of("g", "a", "1.0"),
            skills,
            "Maven: g:a:1.0"
        );
    }
}
