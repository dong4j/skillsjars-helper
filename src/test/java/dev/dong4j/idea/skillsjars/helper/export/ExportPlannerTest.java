package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.InstallationStatus;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ExportPlanner} 6 状态判定测试.
 *
 * <p>每个测试都准备一个真实 jar (避免 mock VFS) + 一个真实磁盘目标目录, 确保覆盖
 * NEW / UP_TO_DATE / OUTDATED / LOCALLY_MODIFIED / FOREIGN / DUPLICATE_NAME 6 种
 * 状态. </p>
 */
class ExportPlannerTest {

    @TempDir
    Path tempDir;

    private final ExportPlanner planner = new ExportPlanner();

    private SkillJarArtifact artifact;
    private SkillDescriptor skill;
    private SkillTargetDirectory target;

    @BeforeEach
    void setUp() throws IOException {
        Path jarPath = this.tempDir.resolve("source.jar");
        writeJarWith(jarPath, new String[]{
            "META-INF/skills/dev/dong4j/code-review/SKILL.md",
            "META-INF/skills/dev/dong4j/code-review/notes.md"
        }, new String[]{
            "---\nname: code-review\n---\nbody",
            "extra"
        });

        this.skill = new SkillDescriptor(
            "code-review", null, List.of(), null,
            "META-INF/skills/dev/dong4j/code-review/",
            "META-INF/skills/dev/dong4j/code-review/SKILL.md",
            "body",
            List.of(
                new SkillFileEntry("SKILL.md", 0),
                new SkillFileEntry("notes.md", 0)
            )
        );

        VirtualFile vf = mock(VirtualFile.class);
        when(vf.getPath()).thenReturn(jarPath.toString());

        this.artifact = new SkillJarArtifact(
            vf,
            SkillSourceType.MAVEN_DEPENDENCY,
            SkillCoordinate.of("dev.dong4j", "zeka-skills", "1.0.0"),
            List.of(this.skill),
            null
        );

        Path agentDir = this.tempDir.resolve(".claude/skills");
        this.target = new SkillTargetDirectory("claude", agentDir, ".claude/skills");
    }

    @Test
    @DisplayName("NEW: 目标目录不存在")
    void status_new_when_target_not_exist() {
        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.NEW);
        assertThat(plan.getTargetDirectoryName()).isEqualTo("code-review");
    }

    @Test
    @DisplayName("FOREIGN: 目标目录存在但没 manifest")
    void status_foreign_when_no_manifest() throws IOException {
        Path skillRoot = this.target.getPath().resolve("code-review");
        Files.createDirectories(skillRoot);
        Files.writeString(skillRoot.resolve("README.md"), "user content");

        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.FOREIGN);
    }

    @Test
    @DisplayName("DUPLICATE_NAME: 已存在的 manifest 来源不一致")
    void status_duplicate_when_different_source() throws IOException {
        Path skillRoot = this.target.getPath().resolve("code-review");
        Files.createDirectories(skillRoot);
        ManifestSchema other = new ManifestSchema(
            1, "skillsjars-helper",
            "other.org:other-skills:1.0.0",
            SkillSourceType.MAVEN_DEPENDENCY,
            "code-review", "shaX",
            "META-INF/skills/other/code-review/",
            "now", "claude", skillRoot.toString(),
            List.of()
        );
        Files.writeString(skillRoot.resolve(ManifestSchema.FILE_NAME), ManifestJson.toJson(other));

        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.DUPLICATE_NAME);
        assertThat(plan.getConflictingArtifactCoordinate()).isEqualTo("other.org:other-skills:1.0.0");
    }

    @Test
    @DisplayName("UP_TO_DATE: manifest 来源一致, jar sha + 文件 sha 都一致")
    void status_up_to_date_when_all_match() throws IOException {
        this.installFromCurrentJar();
        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.UP_TO_DATE);
    }

    @Test
    @DisplayName("LOCALLY_MODIFIED: 安装后改了一个文件")
    void status_locally_modified_when_file_changed() throws IOException {
        this.installFromCurrentJar();
        Path skillRoot = this.target.getPath().resolve("code-review");
        Files.writeString(skillRoot.resolve("notes.md"), "user manually edited");

        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.LOCALLY_MODIFIED);
    }

    @Test
    @DisplayName("OUTDATED: 安装后 jar 变了但本地没动")
    void status_outdated_when_jar_changed() throws IOException {
        this.installFromCurrentJar();
        // 模拟 "jar 升级了" -> 重写 jar 内容, 让 sourceSkillSha 变化
        Path jarPath = Path.of(this.artifact.getJarFile().getPath());
        writeJarWith(jarPath, new String[]{
            "META-INF/skills/dev/dong4j/code-review/SKILL.md",
            "META-INF/skills/dev/dong4j/code-review/notes.md"
        }, new String[]{
            "---\nname: code-review\n---\nNEW body",
            "extra v2"
        });

        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.OUTDATED);
    }

    /**
     * 用一个真实安装结果写入目标目录, 让后续测试有可比对的 manifest.
     */
    private void installFromCurrentJar() throws IOException {
        ExportPlan plan = this.planner.plan(this.artifact, this.skill, this.target);
        // 确保第一次是 NEW
        assertThat(plan.getStatus()).isEqualTo(InstallationStatus.NEW);
        ExportExecutor executor = new ExportExecutor();
        var result = executor.execute(plan);
        assertThat(result.isSuccess()).as("first install should succeed").isTrue();
    }

    private static void writeJarWith(@NotNull Path jarPath, String[] entryNames, String[] contents) throws IOException {
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
}
