package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
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
 * <p>布局: 一个 {@link BorderLayout} 容器, CENTER 是常规 {@link ColoredTreeCellRenderer}
 * (画 skill / artifact 图标 + 文本), EAST 是一个 {@link JLabel} 用来显示 "已安装到的 agent 徽标".</p>
 *
 * <p>渲染规则:</p>
 * <ul>
 *   <li>Artifact 节点: 主标签为 {@code artifactId:version}, 灰色后缀显示来源类型 (Maven / Maven Plugin
 *       等), 图标用 IDEA 内置的 {@code AllIcons.Nodes.PpLib}; 右侧不显示徽标.</li>
 *   <li>Skill 节点: 只显示 skill 名 (左侧 skill 主图标), 紧跟在 skill name 末尾用
 *       {@link SpacedRowIcon} 横排显示已安装到的每个 Agent 品牌图标, 徽标之间留 4 px
 *       间距以避免视觉拥挤 (与 IDEA Project View VCS 标记 / Maven Tool Window conflict
 *       标记的设计语言一致).</li>
 * </ul>
 *
 * <p>间距: 通过 {@code BorderLayout(GAP_NAME_BADGE, 0)} 让 CENTER 与 EAST 之间天然留白,
 * rightLabel 自身额外加右 padding 作为"徽标尾部留白"; 徽标内部间距由 SpacedRowIcon 控制.</p>
 *
 * <p>关于"严格右对齐到 tree 宽度": 该思路在 cell renderer 模式下不可行 — 任何能拿到行
 * 左偏移的 API ({@code tree.getRowBounds} / {@code tree.getPathBounds}) 内部都会
 * 反查 renderer 量尺寸, 形成无限递归 (StackOverflowError); 用 {@code tree.getWidth()}
 * 撑宽又会把 cell 推出可视区. 因此回到"紧跟末尾"的稳态.</p>
 *
 * <p>选中态: 由 panel 自画背景 (用 {@link UIUtil#getTreeSelectionBackground(boolean)}),
 * inner renderer 设为 {@code opaque=false} 避免双层重叠. 文字颜色仍由 inner 的
 * ColoredTreeCellRenderer 按 selected 自动切换 (白字 / 主题色), SpeedSearch 高亮也保留. </p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
final class SkillsTreeCellRenderer extends JBPanel<SkillsTreeCellRenderer> implements TreeCellRenderer {

    /** skill name 与第一个徽标之间的横向间距 (px), 走 BorderLayout hgap. */
    private static final int GAP_NAME_BADGE = 8;
    /** 多个徽标之间的间距 (px). */
    private static final int GAP_BETWEEN_BADGES = 4;
    /** 徽标右侧尾部留白 (px), 避免徽标紧贴 cell 选中态背景的右边. */
    private static final int GAP_TAIL = 4;

    @NotNull
    private final InnerRenderer inner = new InnerRenderer();

    @NotNull
    private final JLabel rightLabel = new JLabel();

    /**
     * skill → 已安装 agentId 集合 的解析器.
     *
     * <p>由 panel 注入, 通常背后是 {@link dev.dong4j.idea.skillsjars.helper.service.InstallationRegistryService}.
     * 解析器外部一次性查询好快照后传 lambda 进来, 渲染时同步访问, 避免 renderer 持有
     * service 引用. </p>
     */
    @NotNull
    private Function<SkillsTreeModel.SkillNode, Collection<String>> installedAgentsResolver =
        node -> Collections.emptyList();

    SkillsTreeCellRenderer() {
        // BorderLayout hgap 让 CENTER 与 EAST 之间天然留白; rightLabel 不可见时
        // BorderLayout 跳过它, hgap 也不会在末尾留多余空白.
        super(new BorderLayout(JBUI.scale(GAP_NAME_BADGE), 0));
        setOpaque(false);
        rightLabel.setOpaque(false);
        rightLabel.setBorder(JBUI.Borders.emptyRight(GAP_TAIL));
        add(inner, BorderLayout.CENTER);
        add(rightLabel, BorderLayout.EAST);
    }

    /**
     * 注入安装状态查询器. 调用方 (面板) 在订阅 InstallationRegistry 变化后, 改完
     * resolver 别忘了 {@code tree.repaint()}.
     */
    void setInstalledAgentsResolver(@NotNull Function<SkillsTreeModel.SkillNode, Collection<String>> resolver) {
        this.installedAgentsResolver = resolver;
    }

    @Override
    public Component getTreeCellRendererComponent(@NotNull JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
        // 1. 让 inner 渲染主体: skill icon + name 或 artifact + source 后缀.
        inner.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        // 让 inner 透明, 选中背景由本 panel 统一画, 避免 inner 自己的高亮把右侧徽标盖住一截.
        inner.setOpaque(false);

        // 2. 算右侧徽标; 仅 skill 节点且有安装位置时显示.
        Icon rightIcon = computeRightIcon(value);
        rightLabel.setIcon(rightIcon);
        rightLabel.setVisible(rightIcon != null);

        // 3. 选中背景: 整行高亮 (cell 自然宽度内).
        if (selected) {
            setBackground(UIUtil.getTreeSelectionBackground(hasFocus));
            setOpaque(true);
        } else {
            setOpaque(false);
        }

        // 4. preferredSize 由 BorderLayout 按 inner + rightLabel 自然累加得出 — 不再
        //    主动设置, 也不调 tree.getRowBounds (它会反查 renderer 触发无限递归).
        //    主动清掉上一帧可能留下的 preferredSize, 让 layout manager 重新计算.
        setPreferredSize(null);
        return this;
    }

    /**
     * 仅 skill 节点 + 至少有一个有效 agent 图标时返回组合徽标, 其他情况返回 null.
     */
    @Nullable
    private Icon computeRightIcon(Object value) {
        if (!(value instanceof DefaultMutableTreeNode node)) {
            return null;
        }
        Object user = node.getUserObject();
        if (!(user instanceof SkillsTreeModel.SkillNode skillNode)) {
            return null;
        }
        Collection<String> agentIds = this.installedAgentsResolver.apply(skillNode);
        if (agentIds.isEmpty()) {
            return null;
        }
        List<Icon> icons = new ArrayList<>(agentIds.size());
        for (String id : agentIds) {
            Icon icon = SkillsJarsHelperIcons.forAgent(id);
            if (icon != null) {
                icons.add(icon);
            }
        }
        return switch (icons.size()) {
            case 0 -> null;
            case 1 -> icons.get(0);
            default -> new SpacedRowIcon(JBUI.scale(GAP_BETWEEN_BADGES), icons);
        };
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

    /**
     * 内部 renderer: 仅负责画 skill / artifact 的左侧 icon + 文本, 完全不感知徽标.
     *
     * <p>选用继承 {@link ColoredTreeCellRenderer} 是为了免费拿到: 选中态文字白色 / SpeedSearch
     * 命中黄色高亮 / 字体度量 / 拖拽预览渲染 等约定.</p>
     */
    private static final class InnerRenderer extends ColoredTreeCellRenderer {

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
                SkillJarArtifact artifact = artifactNode.artifact();
                this.append(artifactNode.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                this.append("  ");
                this.append(formatSource(artifact.getSourceType()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
                this.setIcon(AllIcons.Nodes.PpLib);
            } else if (user instanceof SkillsTreeModel.SkillNode skillNode) {
                this.append(skillNode.skill().getName());
                this.setIcon(SkillsJarsHelperIcons.SKILLSJARS_HELPER_16);
            }
        }
    }

    /**
     * 横向并列的多图标组合, 各图标之间留固定 gap, 高度按最高的图标对齐 (其他图标
     * 垂直居中绘制).
     *
     * <p>为什么不用 IDEA 的 {@link com.intellij.ui.RowIcon}: 它没有图标间距参数,
     * 多个 16x16 logo 紧贴在一起视觉拥挤. 自造一个 13 行的 {@link Icon} 实现最干净,
     * 不引入额外依赖, 也能对每个 logo 视觉中心做垂直居中 (虽然本期所有徽标都是
     * 同高度的 16x16, 但留出兼容空间).</p>
     */
    private static final class SpacedRowIcon implements Icon {

        @NotNull
        private final List<Icon> icons;
        private final int gap;

        SpacedRowIcon(int gap, @NotNull List<Icon> icons) {
            this.gap = gap;
            this.icons = icons;
        }

        @Override
        public int getIconWidth() {
            int w = 0;
            for (int i = 0; i < icons.size(); i++) {
                w += icons.get(i).getIconWidth();
                if (i > 0) {
                    w += gap;
                }
            }
            return w;
        }

        @Override
        public int getIconHeight() {
            int h = 0;
            for (Icon icon : icons) {
                h = Math.max(h, icon.getIconHeight());
            }
            return h;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int totalH = getIconHeight();
            int cx = x;
            for (int i = 0; i < icons.size(); i++) {
                if (i > 0) {
                    cx += gap;
                }
                Icon icon = icons.get(i);
                int yOff = (totalH - icon.getIconHeight()) / 2;
                icon.paintIcon(c, g, cx, y + yOff);
                cx += icon.getIconWidth();
            }
        }
    }
}
