package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;

import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import icons.SkillsJarsHelperIcons;

/**
 * SkillsJars Tool Window 树视图的单元渲染器.
 *
 * <p>渲染规则:</p>
 * <ul>
 *   <li>Artifact 节点: 主标签为 {@code artifactId:version}, 灰色后缀显示来源类型 (Maven / Maven Plugin
 *       等), 图标用 IDEA 内置的 {@code AllIcons.Nodes.PpLib} (jar/library 标准图标).</li>
 *   <li>Skill 节点: 只显示 skill 名, 图标在 skill 主图标的基础上, 用 {@link RowIcon} 横向附加每个
 *       已安装到的 Agent 的品牌徽标. 之前的 "· installed: claude, codex" 文本已被替换以节省横向空间;
 *       多徽标场景 (例如 4-5 个 Agent 并存) 也能保持节点行紧凑. </li>
 * </ul>
 *
 * <p>不在节点上塞 description / allowed-tools, 那部分信息走 tooltip 与状态栏, 保持节点简洁.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
final class SkillsTreeCellRenderer extends ColoredTreeCellRenderer {

    /**
     * skill → 已安装 agentId 集合 的解析器.
     *
     * <p>由 panel 注入, 通常背后是 {@link dev.dong4j.idea.skillsjars.helper.service.InstallationRegistryService}.
     * 解析器外部一次性查询好快照后传 lambda 进来, 渲染时同步访问, 避免 renderer 持有
     * service 引用. </p>
     */
    @NotNull
    private Function<SkillsTreeModel.SkillNode, Collection<String>> installedAgentsResolver = node -> Collections.emptyList();

    /**
     * 注入安装状态查询器. 调用方 (面板) 在订阅 InstallationRegistry 变化后, 改完
     * resolver 别忘了 {@code tree.repaint()}.
     */
    void setInstalledAgentsResolver(@NotNull Function<SkillsTreeModel.SkillNode, Collection<String>> resolver) {
        this.installedAgentsResolver = resolver;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
        if (!(value instanceof DefaultMutableTreeNode node)) {
            return;
        }
        Object user = node.getUserObject();
        if (user instanceof SkillsTreeModel.ArtifactNode artifactNode) {
            this.renderArtifact(artifactNode);
            return;
        }
        if (user instanceof SkillsTreeModel.SkillNode skillNode) {
            this.renderSkill(skillNode);
        }
    }

    private void renderArtifact(@NotNull SkillsTreeModel.ArtifactNode node) {
        SkillJarArtifact artifact = node.artifact();
        this.append(node.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        this.append("  ");
        this.append(formatSource(artifact.getSourceType()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        this.setIcon(AllIcons.Nodes.PpLib);
    }

    private void renderSkill(@NotNull SkillsTreeModel.SkillNode node) {
        this.append(node.skill().getName());
        // 安装徽标用图标替代文本: skill 主图标 + 已安装到的每个 Agent 各一个品牌图标,
        // 横向合成 RowIcon 一起作为节点的左侧图标. 多徽标时仍紧凑, 不会让节点行被文本撑长.
        Collection<String> agentIds = this.installedAgentsResolver.apply(node);
        this.setIcon(buildSkillIcon(agentIds));
    }

    /**
     * 构造 skill 节点的复合图标: 主图标 + 各 agent 徽标横排.
     */
    @NotNull
    private static Icon buildSkillIcon(@NotNull Collection<String> agentIds) {
        Icon main = SkillsJarsHelperIcons.SKILLSJARS_HELPER_16;
        if (agentIds.isEmpty()) {
            return main;
        }
        List<Icon> resolved = new ArrayList<>(agentIds.size() + 1);
        resolved.add(main);
        for (String agentId : agentIds) {
            Icon icon = SkillsJarsHelperIcons.forAgent(agentId);
            if (icon != null) {
                resolved.add(icon);
            }
        }
        if (resolved.size() == 1) {
            return main;
        }
        return new RowIcon(resolved.toArray(new Icon[0]));
    }

    /**
     * 把 {@link SkillSourceType} 渲染为人类可读标签, 与状态栏使用同一份文案.
     */
    @NotNull
    static String formatSource(@NotNull SkillSourceType type) {
        return switch (type) {
            case MAVEN_DEPENDENCY -> "Maven";
            case MAVEN_PLUGIN_DEPENDENCY -> "Maven Plugin";
            case GRADLE_DEPENDENCY -> "Gradle";
            case PROJECT_OUTPUT -> "Module Output";
            case EXTERNAL_LIBRARY -> "External Library";
            case LOCAL_JAR -> "Local Jar";
        };
    }
}
