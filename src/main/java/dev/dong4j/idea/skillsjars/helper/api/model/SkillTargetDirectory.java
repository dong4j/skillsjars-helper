package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Skill 导出的目标 Agent 目录.
 *
 * <p>由 Agent 标识 + 磁盘绝对路径组成. 例如 Claude Code 对应
 * {@code agentId="claude"}, {@code path=<projectRoot>/.claude/skills}.</p>
 *
 * <p>设计选择:</p>
 * <ul>
 *   <li>{@code agentId} 是稳定 ID (claude / codex / junie / cursor / gemini / qoder / trae /
 *       codebuddy / agents), UI 展示文案靠 i18n bundle, 不混在数据模型里.</li>
 *   <li>{@code path} 是 {@link Path} 而不是 {@link com.intellij.openapi.vfs.VirtualFile},
 *       因为目标目录可能尚未存在; VirtualFile 会在 ExportExecutor 执行后再 refresh
 *       回 IDEA. </li>
 *   <li>{@code displayName} 仅供 UI 展示, 例如 ".claude/skills" 这种相对项目根的短
 *       展示形式; 不参与判等.</li>
 * </ul>
 *
 * <p>预设清单与目录命名约定都集中在本类中维护 ({@link #PRESET_AGENT_IDS} +
 * {@link #presetDirName(String)}), 上层探测器 / 索引服务都从这里取, 加新 Agent 时
 * 只需在本类的常量段内新增一条即可.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillTargetDirectory {

    /** 预设 Agent ID 常量, 与 i18n key 后缀对齐; 也是项目根下隐藏目录的名字 (去掉前导 ".") . */
    public static final String AGENT_CLAUDE = "claude";
    public static final String AGENT_CODEX = "codex";
    public static final String AGENT_JUNIE = "junie";
    public static final String AGENT_AGENTS = "agents";
    public static final String AGENT_CURSOR = "cursor";
    public static final String AGENT_GEMINI = "gemini";
    /** Qoder (阿里) — Skill 目录约定 .qoder/skills/{name}/SKILL.md, 同时支持用户级 ~/.qoder/skills. */
    public static final String AGENT_QODER = "qoder";
    /** Trae (字节) — Skill 目录约定 .trae/skills/{name}/SKILL.md, 同时支持用户级 ~/.trae/skills. */
    public static final String AGENT_TRAE = "trae";
    /** CodeBuddy (腾讯) — Skill 目录约定 .codebuddy/skills/{name}/SKILL.md, 同时支持用户级 ~/.codebuddy/skills. */
    public static final String AGENT_CODEBUDDY = "codebuddy";
    /** 用户自定义目录的固定 agentId. */
    public static final String AGENT_CUSTOM = "custom";

    /**
     * 预设 Agent 清单. 单一权威源, 上层 (TargetDirectoryDetector / 文档 / 测试) 都从这里取,
     * 排序即为 UI 菜单中"Extract to ▸"的显示顺序.
     *
     * <p>不包含 {@link #AGENT_CUSTOM}, 因为自定义目录由用户当场指定路径, 不属于"预设".</p>
     */
    public static final List<String> PRESET_AGENT_IDS = List.of(
        AGENT_CLAUDE,
        AGENT_CODEX,
        AGENT_JUNIE,
        AGENT_AGENTS,
        AGENT_CURSOR,
        AGENT_GEMINI,
        AGENT_QODER,
        AGENT_TRAE,
        AGENT_CODEBUDDY
    );

    /**
     * 预设目录命名约定: 项目根下隐藏目录就是 {@code "." + agentId}, 例如
     * {@code claude → .claude}, {@code codebuddy → .codebuddy}.
     *
     * <p>这是当前所有官方 Agent (Claude Code / Codex / Junie / Cursor / Gemini / Qoder /
     * Trae / CodeBuddy) 都遵循的事实标准, 抽出来避免在 detector 里再硬编码 ".claude" 这类
     * 字面量与本类常量重复书写.</p>
     *
     * @param agentId 预设 agentId, 必须取自 {@link #PRESET_AGENT_IDS} 之一
     * @return 形如 ".claude" 的隐藏目录名 (相对项目根)
     */
    @NotNull
    public static String presetDirName(@NotNull String agentId) {
        return "." + agentId;
    }

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
