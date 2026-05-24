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
    // 各 Agent 的简化品牌图标, 用于 ToolWindow 树叶子的右侧徽标, 表明该 skill 已安装到对应
    // Agent. 几何简化版仅保留品牌识别度, 不直接复制官方 logo, 规避版权风险.
    //
    // 颜色策略: Claude (terra cotta) / Junie (lawn green) / Gemini (蓝紫渐变) 用品牌色,
    // light/dark 主题下都能识别; Codex (黑) / Cursor (黑) / Agents (单色) 使用 currentColor,
    // 由 IDEA 自动跟随主题反相.

    /** Claude (Anthropic) 简化星型图标. */
    public static final Icon AGENT_CLAUDE = load("/icons/agents/claude.svg");
    /** Codex (OpenAI) 简化六瓣花图标. */
    public static final Icon AGENT_CODEX = load("/icons/agents/codex.svg");
    /** Junie (JetBrains) 绿色方块字母图标. */
    public static final Icon AGENT_JUNIE = load("/icons/agents/junie.svg");
    /** Cursor 经典光标三角图标. */
    public static final Icon AGENT_CURSOR = load("/icons/agents/cursor.svg");
    /** Gemini (Google) 蓝紫渐变四角星图标. */
    public static final Icon AGENT_GEMINI = load("/icons/agents/gemini.svg");
    /** Agents (通用) 六边形 + 节点图标. */
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
