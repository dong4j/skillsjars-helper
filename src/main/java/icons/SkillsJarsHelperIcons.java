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
 * @date 2026.05.25
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
    // 各 Agent 的品牌图标, 用于 ToolWindow 树叶子的右侧徽标, 表明该 skill 已安装到对应
    // Agent. 资产为 PNG 透明背景, 经 Lanczos 缩小到 16/32 px.
    //
    // HiDPI 约定: 每个图标提供两份, name.png (16x16) + name@2x.png (32x32),
    // IDEA 在 200% 缩放屏 (Retina) 上自动加载 @2x 版本; IconLoader 只需指向 16x16
    // 路径, @2x 自动按命名约定解析.
    //
    // 主题策略: 全部使用品牌彩色 PNG, 不参与 IDEA 主题反相 — 品牌色本身就是识别度,
    // 强行单色化反而失真. 通用 agents 是兜底图.

    /** Claude (Anthropic). */
    public static final Icon AGENT_CLAUDE = load("/icons/agents/claude.png");
    /** Codex (OpenAI). */
    public static final Icon AGENT_CODEX = load("/icons/agents/codex.png");
    /** Junie (JetBrains). */
    public static final Icon AGENT_JUNIE = load("/icons/agents/junie.png");
    /** Cursor. */
    public static final Icon AGENT_CURSOR = load("/icons/agents/cursor.png");
    /** Gemini (Google). */
    public static final Icon AGENT_GEMINI = load("/icons/agents/gemini.png");
    /** Qoder (阿里). */
    public static final Icon AGENT_QODER = load("/icons/agents/qoder.png");
    /** Trae (字节). */
    public static final Icon AGENT_TRAE = load("/icons/agents/trae.png");
    /** CodeBuddy (腾讯). */
    public static final Icon AGENT_CODEBUDDY = load("/icons/agents/codebuddy.png");
    /** Agents (通用兜底, 无官方品牌). */
    public static final Icon AGENT_AGENTS = load("/icons/agents/agents.png");

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
            case SkillTargetDirectory.AGENT_QODER -> AGENT_QODER;
            case SkillTargetDirectory.AGENT_TRAE -> AGENT_TRAE;
            case SkillTargetDirectory.AGENT_CODEBUDDY -> AGENT_CODEBUDDY;
            case SkillTargetDirectory.AGENT_AGENTS -> AGENT_AGENTS;
            case SkillTargetDirectory.AGENT_CUSTOM -> AllIcons.Nodes.Folder;
            default -> null;
        };
    }
}
