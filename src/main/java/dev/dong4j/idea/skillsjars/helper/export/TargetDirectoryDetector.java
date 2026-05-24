package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;

/**
 * 目标 Agent 目录探测器.
 *
 * <p>本期固定 8 个预设 Agent 目录, 全部相对项目根:</p>
 * <ul>
 *   <li>{@code .claude/skills} (Claude Code)</li>
 *   <li>{@code .codex/skills} (Codex)</li>
 *   <li>{@code .junie/skills} (Junie)</li>
 *   <li>{@code .agents/skills} (通用)</li>
 *   <li>{@code .cursor/skills} (Cursor)</li>
 *   <li>{@code .gemini/skills} (Gemini Code Assist)</li>
 *   <li>{@code .qoder/skills} (Qoder, 阿里出品)</li>
 *   <li>{@code .trae/skills} (Trae, 字节出品)</li>
 * </ul>
 *
 * <p>不在此处过滤"目录不存在"的候选: 目录可能要由本插件自动创建, UI 自己决定如何展示.</p>
 *
 * <p>"自定义目录"由 UI 通过 {@link #customTarget(Path)} 即时构造, 不在预设列表里.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class TargetDirectoryDetector {

    private TargetDirectoryDetector() {
    }

    /**
     * 探测当前项目可用的预设目录.
     *
     * @param project 当前项目, 用于取 basePath
     * @return 8 个预设候选; project 没有 basePath 时返回空列表
     */
    @NotNull
    public static List<SkillTargetDirectory> detect(@NotNull Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return List.of();
        }
        return detect(Path.of(basePath));
    }

    /**
     * 用给定 root 探测预设目录, 主要给单元测试使用.
     */
    @NotNull
    public static List<SkillTargetDirectory> detect(@NotNull Path projectRoot) {
        List<SkillTargetDirectory> out = new ArrayList<>(8);
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_CLAUDE, ".claude"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_CODEX, ".codex"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_JUNIE, ".junie"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_AGENTS, ".agents"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_CURSOR, ".cursor"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_GEMINI, ".gemini"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_QODER, ".qoder"));
        out.add(buildPreset(projectRoot, SkillTargetDirectory.AGENT_TRAE, ".trae"));
        return out;
    }

    /**
     * 构造一个用户自定义目录. agentId 固定为 {@link SkillTargetDirectory#AGENT_CUSTOM}.
     */
    @NotNull
    public static SkillTargetDirectory customTarget(@NotNull Path absolutePath) {
        return new SkillTargetDirectory(
            SkillTargetDirectory.AGENT_CUSTOM,
            absolutePath,
            absolutePath.toString()
        );
    }

    /**
     * 在预设列表里按 agentId 找一项, 没有返回 null.
     */
    @Nullable
    public static SkillTargetDirectory findByAgentId(@NotNull List<SkillTargetDirectory> targets,
                                                    @NotNull String agentId) {
        for (SkillTargetDirectory t : targets) {
            if (t.getAgentId().equals(agentId)) {
                return t;
            }
        }
        return null;
    }

    @NotNull
    private static SkillTargetDirectory buildPreset(@NotNull Path projectRoot,
                                                    @NotNull String agentId,
                                                    @NotNull String agentDirName) {
        Path absolute = projectRoot.resolve(agentDirName).resolve("skills");
        String displayName = agentDirName + "/skills";
        return new SkillTargetDirectory(agentId, absolute, displayName);
    }
}
