package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 单个 Skill 导出到指定 Agent 目录的 "执行计划".
 *
 * <p>由 {@code ExportPlanner} 产出, 由 {@code ExportExecutor} 消费. UI 也可以读取
 * 该计划的 {@code status} 来决定是否需要弹窗、弹什么文案. 计划是不可变的, 但执行阶段
 * 的最终落盘目录名 (考虑 {@link InstallationStatus#DUPLICATE_NAME} 时的 fallback)
 * 由调用方在执行前决定后写到 {@link #targetDirectoryName}.</p>
 *
 * <p>关键字段:</p>
 * <ul>
 *   <li>{@link #status}: 6 状态机的判定结果, 决定后续交互.</li>
 *   <li>{@link #targetDirectory}: 用户选的 Agent 父目录 (如 .claude/skills).</li>
 *   <li>{@link #targetDirectoryName}: 该 skill 落地的 <b>子目录名</b>. 默认是
 *       frontmatter 的 {@code name}; 用户在 DUPLICATE_NAME 场景下选 "改用后缀名"
 *       时, 调用方会把这里替换成 {@code <name>__<artifactId>}.</li>
 *   <li>{@link #targetSkillRoot}: 等价于 {@code targetDirectory.path / targetDirectoryName},
 *       用于减少调用方拼接.</li>
 *   <li>{@link #conflictingArtifactCoordinate}: 仅在 {@link InstallationStatus#DUPLICATE_NAME}
 *       时有值, 指向占用同名目录的另一个 skill 来源坐标, UI 用它给出明确的冲突说明.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExportPlan {

    @NotNull
    private final InstallationStatus status;

    @NotNull
    private final SkillJarArtifact artifact;

    @NotNull
    private final SkillDescriptor skill;

    @NotNull
    private final SkillTargetDirectory targetDirectory;

    @NotNull
    private final String targetDirectoryName;

    @NotNull
    private final Path targetSkillRoot;

    @NotNull
    private final List<SkillFileEntry> sourceFiles;

    @Nullable
    private final String conflictingArtifactCoordinate;

    public ExportPlan(@NotNull InstallationStatus status,
                      @NotNull SkillJarArtifact artifact,
                      @NotNull SkillDescriptor skill,
                      @NotNull SkillTargetDirectory targetDirectory,
                      @NotNull String targetDirectoryName,
                      @NotNull Path targetSkillRoot,
                      @NotNull List<SkillFileEntry> sourceFiles,
                      @Nullable String conflictingArtifactCoordinate) {
        this.status = status;
        this.artifact = artifact;
        this.skill = skill;
        this.targetDirectory = targetDirectory;
        this.targetDirectoryName = targetDirectoryName;
        this.targetSkillRoot = targetSkillRoot;
        this.sourceFiles = List.copyOf(sourceFiles);
        this.conflictingArtifactCoordinate = conflictingArtifactCoordinate;
    }

    @NotNull
    public InstallationStatus getStatus() {
        return this.status;
    }

    @NotNull
    public SkillJarArtifact getArtifact() {
        return this.artifact;
    }

    @NotNull
    public SkillDescriptor getSkill() {
        return this.skill;
    }

    @NotNull
    public SkillTargetDirectory getTargetDirectory() {
        return this.targetDirectory;
    }

    @NotNull
    public String getTargetDirectoryName() {
        return this.targetDirectoryName;
    }

    @NotNull
    public Path getTargetSkillRoot() {
        return this.targetSkillRoot;
    }

    @NotNull
    public List<SkillFileEntry> getSourceFiles() {
        return Collections.unmodifiableList(this.sourceFiles);
    }

    @Nullable
    public String getConflictingArtifactCoordinate() {
        return this.conflictingArtifactCoordinate;
    }

    /**
     * 复制本计划但替换目标目录名, 用于 DUPLICATE_NAME 场景下用户选 "改用后缀名".
     *
     * @param newDirectoryName 新目录名, 例如 {@code code-review__zeka-skills}
     * @return 新的不可变计划
     */
    @NotNull
    public ExportPlan withTargetDirectoryName(@NotNull String newDirectoryName) {
        Path newRoot = this.targetDirectory.getPath().resolve(newDirectoryName);
        return new ExportPlan(
            this.status,
            this.artifact,
            this.skill,
            this.targetDirectory,
            newDirectoryName,
            newRoot,
            this.sourceFiles,
            this.conflictingArtifactCoordinate
        );
    }
}
