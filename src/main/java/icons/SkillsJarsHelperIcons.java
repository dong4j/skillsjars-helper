package icons;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;

/**
 * 图标工具类
 * <p> 提供常用图标资源的加载和访问功能, 用于应用程序中各种界面元素的图标展示
 *
 * @author dong4j
 * @version 1.0.0
 * @email "mailto:dong4j@gmail.com"
 * @date 2026.01.02
 * @since 1.0.0
 */
public class SkillsJarsHelperIcons {
    /**
     * 加载图标
     * <p> 用于加载位于资源包路径下的图标文件. 路径应与插件包路径保持一致.</p>
     *
     * @param iconPath 图标文件路径, 相对于 resources 根目录
     * @return 加载的图标
     */
    @NotNull
    private static Icon load(@NotNull String iconPath) {
        return IconLoader.getIcon(iconPath, SkillsJarsHelperIcons.class);
    }

    // ========== 16x16 图标 - 用于 Toolbar/Action/Menu/ToolWindow ==========

    /** 插件主图标 (16x16), 用于工具栏按钮, 动作图标, 菜单项及工具窗口标签 */
    public static final Icon SKILLSJARS_HELPER_16 =
        load("/icons/skillsjars_helper_16.svg");

    // ========== Agent 安装状态徽标图标 (16x16) ==========
    //
    // 各 Agent 的官方品牌图标, 用于 ToolWindow 树叶子的右侧徽标, 表明该 skill 已安装到对应
    // Agent. 5 个品牌图标 (Claude / Codex / Junie / Cursor / Gemini) 来自 @lobehub/icons
    // 静态 SVG (MIT, github.com/lobehub/lobe-icons), 仅做尺寸/无关属性的最小裁剪, path 数据
    // 与渐变保持原样; 详细版权声明见项目根 NOTICE 文件.
    //
    // 主题策略: claude / junie / gemini 用 -color 彩色版, 品牌识别度优先, light/dark 都能看清;
    // codex / cursor 用 mono 版 (fill="currentColor"), 自动跟随 IDEA 主题反相; 通用 agents
    // 没有官方资产, 使用项目自绘的几何兜底图标 (六边形 + 节点).

    /** Claude (Anthropic). 来源: @lobehub/icons claude-color.svg. */
    public static final Icon AGENT_CLAUDE = load("/icons/agents/claude.svg");
    /** Codex (OpenAI). 来源: @lobehub/icons codex.svg (mono, currentColor). */
    public static final Icon AGENT_CODEX = load("/icons/agents/codex.svg");
    /** Junie (JetBrains). 来源: @lobehub/icons junie-color.svg. */
    public static final Icon AGENT_JUNIE = load("/icons/agents/junie.svg");
    /** Cursor. 来源: @lobehub/icons cursor.svg (mono, currentColor). */
    public static final Icon AGENT_CURSOR = load("/icons/agents/cursor.svg");
    /** Gemini (Google). 来源: @lobehub/icons gemini-color.svg. */
    public static final Icon AGENT_GEMINI = load("/icons/agents/gemini.svg");
    /** Agents (通用). 项目自绘几何兜底图标, 无官方品牌. */
    public static final Icon AGENT_AGENTS = load("/icons/agents/agents.svg");

    /**
     * 按 agentId 取对应的徽标图标.
     *
     * <p>未知 agentId (例如 {@link SkillTargetDirectory#AGENT_CUSTOM}) 退化为 IDEA 内置的
     * 文件夹图标, 表示 "已安装到自定义目录".</p>
     *
     * @param agentId Agent ID, 通常来自 {@link SkillTargetDirectory} 的常量
     * @return 对应图标; 完全无法识别时返回 null, 调用方自行决定 fallback
     */
    @Nullable
    public static Icon forAgent(@NotNull String agentId) {
        return switch (agentId) {
            case SkillTargetDirectory.AGENT_CLAUDE -> AGENT_CLAUDE;
            case SkillTargetDirectory.AGENT_CODEX -> AGENT_CODEX;
            case SkillTargetDirectory.AGENT_JUNIE -> AGENT_JUNIE;
            case SkillTargetDirectory.AGENT_CURSOR -> AGENT_CURSOR;
            case SkillTargetDirectory.AGENT_GEMINI -> AGENT_GEMINI;
            case SkillTargetDirectory.AGENT_AGENTS -> AGENT_AGENTS;
            case SkillTargetDirectory.AGENT_CUSTOM -> AllIcons.Nodes.Folder;
            default -> null;
        };
    }
}
