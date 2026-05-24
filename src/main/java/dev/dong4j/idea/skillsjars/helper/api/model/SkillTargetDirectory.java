package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Skill 导出的目标 Agent 目录.
 *
 * <p>由 Agent 标识 + 磁盘绝对路径组成. 例如 Claude Code 对应
 * {@code agentId="claude"}, {@code path=<projectRoot>/.claude/skills}.</p>
 *
 * <p>设计选择:</p>
 * <ul>
 *   <li>{@code agentId} 是稳定 ID (claude / codex / junie / cursor / gemini / qoder / agents),
 *       UI 展示文案靠 i18n bundle, 不混在数据模型里.</li>
 *   <li>{@code path} 是 {@link Path} 而不是 {@link com.intellij.openapi.vfs.VirtualFile},
 *       因为目标目录可能尚未存在; VirtualFile 会在 ExportExecutor 执行后再 refresh
 *       回 IDEA. </li>
 *   <li>{@code displayName} 仅供 UI 展示, 例如 ".claude/skills" 这种相对项目根的短
 *       展示形式; 不参与判等.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillTargetDirectory {

    /** 预设 Agent ID 常量, 与 i18n key 后缀对齐. */
    public static final String AGENT_CLAUDE = "claude";
    public static final String AGENT_CODEX = "codex";
    public static final String AGENT_JUNIE = "junie";
    public static final String AGENT_AGENTS = "agents";
    public static final String AGENT_CURSOR = "cursor";
    public static final String AGENT_GEMINI = "gemini";
    /** Qoder (阿里) — Skill 目录约定 .qoder/skills/{name}/SKILL.md, 同时支持用户级 ~/.qoder/skills. */
    public static final String AGENT_QODER = "qoder";
    /** 用户自定义目录的固定 agentId. */
    public static final String AGENT_CUSTOM = "custom";

    @NotNull
    private final String agentId;

    @NotNull
    private final Path path;

    @NotNull
    private final String displayName;

    public SkillTargetDirectory(@NotNull String agentId,
                                @NotNull Path path,
                                @NotNull String displayName) {
        this.agentId = agentId;
        this.path = path;
        this.displayName = displayName;
    }

    @NotNull
    public String getAgentId() {
        return this.agentId;
    }

    @NotNull
    public Path getPath() {
        return this.path;
    }

    @NotNull
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillTargetDirectory that)) {
            return false;
        }
        return Objects.equals(this.agentId, that.agentId)
            && Objects.equals(this.path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.agentId, this.path);
    }

    @Override
    public String toString() {
        return "SkillTargetDirectory{agent=" + this.agentId + ", path=" + this.path + '}';
    }
}
