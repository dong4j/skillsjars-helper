package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

import java.util.Collection;
import java.util.Collections;
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
 *   <li>Skill 节点: 只显示 skill 名, 图标用本插件的 16x16 图标, 风格上与 jar 节点形成层级.</li>
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
        this.setIcon(SkillsJarsHelperIcons.SKILLSJARS_HELPER_16);
        // 安装徽标: 灰色 "· installed: claude, codex"
        // 把多个 agent 收敛成一个紧凑标签, 节点行不至于过宽.
        String badge = formatInstalledBadge(this.installedAgentsResolver.apply(node));
        if (badge != null) {
            this.append("  ");
            this.append(badge, SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
    }

    @Nullable
    private static String formatInstalledBadge(@NotNull Collection<String> agentIds) {
        if (agentIds.isEmpty()) {
            return null;
        }
        return "· installed: " + String.join(", ", agentIds);
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
