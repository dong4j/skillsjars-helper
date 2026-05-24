package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ui.JBUI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.SkillRegistry;
import dev.dong4j.idea.skillsjars.helper.api.SkillRegistryListener;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.util.SkillsJarsHelperBundle;

import icons.SkillsJarsHelperIcons;

/**
 * SkillsJars Tool Window 主面板 (树形版本).
 *
 * <p>布局:</p>
 * <ul>
 *   <li>顶部 Toolbar: Refresh / Expand All / Collapse All.</li>
 *   <li>中部 Tree: 按 artifact 折叠 skill (一层分组), 启用 SpeedSearch、Tooltip、空状态文本.</li>
 *   <li>底部 Status Bar: 跟随选中变化, 把 description / allowed-tools 等被截断的字段补回来.</li>
 * </ul>
 *
 * <p>交互:</p>
 * <ul>
 *   <li>双击叶子 skill 节点 → 打开 jar 内 SKILL.md (artifact 节点保留默认展开/折叠行为).</li>
 *   <li>Enter 键: 选中 skill 时打开 SKILL.md, 选中 artifact 时切换展开/折叠.</li>
 *   <li>右键: Open SKILL.md / Copy Skill Name / Copy Coordinate / Open Source Jar.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillsToolWindowPanel extends JPanel implements Disposable {

    private static final String TOOLBAR_PLACE = "SkillsJarsHelperToolbar";
    private static final String POPUP_PLACE = "SkillsJarsHelperPopup";
    /** OnePixelSplitter 持久化分割比例的 key (IDEA 标准做法, 对每个用户独立保存). */
    private static final String SPLITTER_PROPORTION_KEY = "SkillsJarsHelper.splitter.proportion";
    /** 描述面板默认占比 (上 70% 给树, 下 30% 给描述). */
    private static final float DEFAULT_SPLITTER_PROPORTION = 0.7f;

    @NotNull
    private final Project project;

    @NotNull
    private final SkillRegistry registry;

    @NotNull
    private final Tree tree;

    /** 描述面板: 多行只读, 跟随选中变化, 是状态栏不擅长展示长文本时的补充. */
    @NotNull
    private final JBTextArea descriptionArea;

    @NotNull
    private final JBLabel statusLabel;

    /**
     * 构造面板.
     *
     * @param project 当前项目
     */
    public SkillsToolWindowPanel(@NotNull Project project) {
        super(new BorderLayout());
        this.project = project;
        this.registry = SkillRegistry.getInstance(project);

        this.tree = this.createTree();
        this.descriptionArea = this.createDescriptionArea();
        this.statusLabel = new JBLabel(SkillsJarsHelperBundle.message("toolwindow.status.empty"));
        this.statusLabel.setBorder(JBUI.Borders.empty(4, 8));

        this.installTreeBehaviors();

        // 上下分屏: 上为树, 下为描述面板; 用户可拖动分割条, 位置自动持久化
        OnePixelSplitter splitter = new OnePixelSplitter(true, SPLITTER_PROPORTION_KEY, DEFAULT_SPLITTER_PROPORTION);
        splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(this.tree));
        splitter.setSecondComponent(this.createDescriptionPanel());

        this.add(this.createToolbar(), BorderLayout.NORTH);
        this.add(splitter, BorderLayout.CENTER);
        this.add(this.statusLabel, BorderLayout.SOUTH);

        // 订阅 registry 变更, 自动刷新
        Disposable subscription = this.registry.addListener(new SkillRegistryListener() {
            @Override
            public void onSkillsRefreshed(@NotNull List<SkillJarArtifact> artifacts) {
                ApplicationManager.getApplication().invokeLater(
                    () -> SkillsToolWindowPanel.this.applySnapshot(artifacts),
                    project.getDisposed()
                );
            }
        });
        Disposer.register(this, subscription);

        this.applySnapshot(this.registry.getArtifacts());
        this.registry.refresh();
    }

    @Override
    public void dispose() {
        // 监听器订阅已通过 Disposer.register 关联到本面板, 此处无需额外释放
    }

    // ─────────────────────── 初始化 ───────────────────────

    /**
     * 创建 Tree, 并附加 emptyText / 渲染器 / SpeedSearch / Tooltip.
     */
    @NotNull
    private Tree createTree() {
        Tree tree = new Tree(SkillsTreeModel.build(Collections.emptyList())) {
            /**
             * 重写 tooltip 以根据节点类型展示不同内容.
             * <p>避免在渲染器里塞副文本, 保持节点行高紧凑; 详细信息只在 hover 时浮出.</p>
             */
            @Override
            public String getToolTipText(MouseEvent event) {
                TreePath path = this.getPathForLocation(event.getX(), event.getY());
                Object user = SkillsTreeModel.userObject(path);
                if (user instanceof SkillsTreeModel.SkillNode skillNode) {
                    return buildSkillTooltip(skillNode);
                }
                if (user instanceof SkillsTreeModel.ArtifactNode artifactNode) {
                    return buildArtifactTooltip(artifactNode);
                }
                return null;
            }
        };
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new SkillsTreeCellRenderer());

        // 空状态: 居中提示 + 链接形态的 Refresh
        tree.getEmptyText()
            .setText(SkillsJarsHelperBundle.message("toolwindow.empty.text"))
            .appendSecondaryText(
                SkillsJarsHelperBundle.message("toolwindow.empty.refresh"),
                SimpleTextAttributes.LINK_ATTRIBUTES,
                e -> this.registry.refresh()
            );

        ToolTipManager.sharedInstance().registerComponent(tree);
        TreeSpeedSearch.installOn(tree);
        return tree;
    }

    /**
     * 创建描述区文本控件.
     *
     * <p>多行、只读、自动按词换行, 字体颜色跟随 IDEA 主题; 用户可选中复制片段.</p>
     */
    @NotNull
    private JBTextArea createDescriptionArea() {
        JBTextArea area = new JBTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(JBUI.Borders.empty(8, 12));
        area.setText(SkillsJarsHelperBundle.message("toolwindow.detail.empty"));
        return area;
    }

    /**
     * 把描述区包成一个带滚动条、无边框的小面板, 嵌入 splitter 下半区.
     */
    @NotNull
    private JComponent createDescriptionPanel() {
        JBScrollPane scroll = new JBScrollPane(this.descriptionArea);
        scroll.setBorder(JBUI.Borders.empty());
        return scroll;
    }

    /**
     * 给 tree 装上鼠标 / 键盘 / 选择 / 右键 行为.
     */
    private void installTreeBehaviors() {
        // 双击叶子 → 打开 SKILL.md; 双击非叶子保留默认展开/折叠行为
        EditSourceOnDoubleClickHandler.install(this.tree, this::openSelected);

        // Enter 键: 叶子打开 SKILL.md, 非叶子切换展开
        String key = "skillsjars-helper.openSelected";
        this.tree.getInputMap(JComponent.WHEN_FOCUSED)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), key);
        this.tree.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                TreePath selected = SkillsToolWindowPanel.this.tree.getSelectionPath();
                if (selected == null) {
                    return;
                }
                Object user = SkillsTreeModel.userObject(selected);
                if (user instanceof SkillsTreeModel.SkillNode) {
                    SkillsToolWindowPanel.this.openSelected();
                } else if (SkillsToolWindowPanel.this.tree.isExpanded(selected)) {
                    SkillsToolWindowPanel.this.tree.collapsePath(selected);
                } else {
                    SkillsToolWindowPanel.this.tree.expandPath(selected);
                }
            }
        });

        // 选中状态变化 → 仅刷新描述面板 (状态栏只展示全局汇总, 不再随选中抖动)
        this.tree.addTreeSelectionListener(e -> this.updateDescriptionPanel());

        // 右键弹出菜单
        DefaultActionGroup popupGroup = new DefaultActionGroup();
        popupGroup.add(new OpenSkillMdAction());
        popupGroup.addSeparator();
        popupGroup.add(new CopySkillNameAction());
        popupGroup.add(new CopyCoordinateAction());
        popupGroup.addSeparator();
        popupGroup.add(new OpenSourceJarAction());
        PopupHandler.installPopupMenu(this.tree, popupGroup, POPUP_PLACE);
    }

    /**
     * 创建顶部工具栏.
     */
    @NotNull
    private JPanel createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        group.add(new RefreshAction());
        group.addSeparator();
        group.add(new ExpandAllAction());
        group.add(new CollapseAllAction());

        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true);
        toolbar.setTargetComponent(this);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(toolbar.getComponent(), BorderLayout.WEST);
        return wrapper;
    }

    // ─────────────────────── 数据/状态 ───────────────────────

    /**
     * 应用最新快照: 重建模型并默认展开所有 artifact 节点 (一期 jar 通常不多, 默认展开比折叠更友好).
     */
    private void applySnapshot(@NotNull List<SkillJarArtifact> artifacts) {
        DefaultTreeModel model = SkillsTreeModel.build(artifacts);
        this.tree.setModel(model);
        this.expandAllArtifactNodes();
        this.updateStatusBar();
        this.updateDescriptionPanel();
    }

    /**
     * 把所有 artifact 节点展开.
     */
    private void expandAllArtifactNodes() {
        for (int i = 0; i < this.tree.getRowCount(); i++) {
            this.tree.expandRow(i);
        }
    }

    /**
     * 状态栏只展示全局汇总, 不再跟随选中变化 (选中信息走描述面板).
     */
    private void updateStatusBar() {
        List<SkillJarArtifact> artifacts = this.registry.getArtifacts();
        int totalSkills = 0;
        for (SkillJarArtifact artifact : artifacts) {
            totalSkills += artifact.getSkills().size();
        }
        if (totalSkills == 0) {
            this.statusLabel.setText(SkillsJarsHelperBundle.message("toolwindow.status.empty"));
        } else {
            this.statusLabel.setText(SkillsJarsHelperBundle.message(
                "toolwindow.status.summary", totalSkills, artifacts.size()));
        }
    }

    /**
     * 描述面板只展示 skill 的 description.
     *
     * <ul>
     *   <li>选中 skill 且 description 有内容 → 直接展示原文</li>
     *   <li>选中 skill 但 description 为空 → 展示 "(no description provided)"</li>
     *   <li>其他情况 (未选中 / 选中 artifact) → 展示 "Select a skill ..." 占位</li>
     * </ul>
     */
    private void updateDescriptionPanel() {
        Object user = SkillsTreeModel.userObject(this.tree.getSelectionPath());
        String text;
        if (user instanceof SkillsTreeModel.SkillNode skillNode) {
            SkillDescriptor skill = skillNode.skill();
            String description = skill.getDescription();
            text = description == null || description.isBlank()
                ? SkillsJarsHelperBundle.message("toolwindow.detail.noDescription")
                : description;
        } else {
            text = SkillsJarsHelperBundle.message("toolwindow.detail.empty");
        }
        this.descriptionArea.setText(text);
        this.descriptionArea.setCaretPosition(0);
    }

    // ─────────────────────── 行为 ───────────────────────

    /**
     * 打开当前选中的 skill 对应的 SKILL.md.
     *
     * <p>策略: 从 jar 文件还原 jar 内根目录, 再按 {@code skillMdPath} 查找; 找不到时退化为打开 jar 文件,
     * 让 IDEA 内置的 jar 浏览器接管.</p>
     */
    private void openSelected() {
        Object user = SkillsTreeModel.userObject(this.tree.getSelectionPath());
        if (!(user instanceof SkillsTreeModel.SkillNode skillNode)) {
            return;
        }
        VirtualFile jar = skillNode.artifact().getJarFile();
        VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
        VirtualFile target = jarRoot != null
            ? jarRoot.findFileByRelativePath(skillNode.skill().getSkillMdPath())
            : null;
        if (target == null) {
            target = jar;
        }
        FileEditorManager.getInstance(this.project).openFile(target, true);
    }

    /**
     * 取当前选中的 SkillNode, 没有则返回 null.
     */
    @Nullable
    private SkillsTreeModel.SkillNode selectedSkill() {
        Object user = SkillsTreeModel.userObject(this.tree.getSelectionPath());
        return user instanceof SkillsTreeModel.SkillNode skillNode ? skillNode : null;
    }

    /**
     * 取当前选中节点对应的 jar artifact (skill 节点也能反查).
     */
    @Nullable
    private SkillJarArtifact selectedArtifact() {
        Object user = SkillsTreeModel.userObject(this.tree.getSelectionPath());
        if (user instanceof SkillsTreeModel.ArtifactNode artifactNode) {
            return artifactNode.artifact();
        }
        if (user instanceof SkillsTreeModel.SkillNode skillNode) {
            return skillNode.artifact();
        }
        return null;
    }

    private static void copyToClipboard(@NotNull String text) {
        // 兼容: CopyPasteManager 在 IDE 内可用; AWT Toolkit 兜底用于无 IDE 场景的健壮性
        try {
            CopyPasteManager.getInstance().setContents(new StringSelection(text));
        } catch (Throwable ignored) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    // ─────────────────────── Tooltip ───────────────────────

    /**
     * 构造 skill 节点的 HTML tooltip.
     * <p>只展示存在的字段, 缺失字段不输出对应行.</p>
     */
    @NotNull
    private static String buildSkillTooltip(@NotNull SkillsTreeModel.SkillNode node) {
        SkillDescriptor skill = node.skill();
        StringBuilder sb = new StringBuilder("<html><b>");
        sb.append(escape(skill.getName())).append("</b>");
        if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
            sb.append("<br>").append(escape(skill.getDescription()));
        }
        if (!skill.getAllowedTools().isEmpty()) {
            sb.append("<br><small>Allowed: ")
                .append(escape(String.join(", ", skill.getAllowedTools())))
                .append("</small>");
        }
        sb.append("<br><small>")
            .append(escape(node.artifact().getCoordinate().toCoordinateString()))
            .append("</small></html>");
        return sb.toString();
    }

    /**
     * 构造 artifact 节点的 HTML tooltip.
     */
    @NotNull
    private static String buildArtifactTooltip(@NotNull SkillsTreeModel.ArtifactNode node) {
        SkillJarArtifact artifact = node.artifact();
        return "<html><b>" + escape(artifact.getCoordinate().toCoordinateString()) + "</b>"
            + "<br><small>" + escape(SkillsTreeCellRenderer.formatSource(artifact.getSourceType())) + "</small>"
            + "<br><small>" + escape(artifact.getJarFile().getPath()) + "</small></html>";
    }

    @NotNull
    private static String escape(@NotNull String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ─────────────────────── 内嵌 Action 类 ───────────────────────

    /**
     * 工具栏 Refresh 动作.
     */
    private final class RefreshAction extends AnAction {

        RefreshAction() {
            super(
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.refresh.title"),
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.refresh.description"),
                AllIcons.Actions.Refresh
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.BGT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            SkillsToolWindowPanel.this.registry.refresh();
        }
    }

    /**
     * 工具栏 Expand All.
     */
    private final class ExpandAllAction extends AnAction {

        ExpandAllAction() {
            super(
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.expandAll.title"),
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.expandAll.description"),
                AllIcons.Actions.Expandall
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(SkillsToolWindowPanel.this.tree.getRowCount() > 0);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // expandRow 在迭代过程中行号会增加, 需要持续到稳定
            int last = -1;
            while (last != SkillsToolWindowPanel.this.tree.getRowCount()) {
                last = SkillsToolWindowPanel.this.tree.getRowCount();
                for (int i = 0; i < last; i++) {
                    SkillsToolWindowPanel.this.tree.expandRow(i);
                }
            }
        }
    }

    /**
     * 工具栏 Collapse All.
     */
    private final class CollapseAllAction extends AnAction {

        CollapseAllAction() {
            super(
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.collapseAll.title"),
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.collapseAll.description"),
                AllIcons.Actions.Collapseall
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(SkillsToolWindowPanel.this.tree.getRowCount() > 0);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            for (int i = SkillsToolWindowPanel.this.tree.getRowCount() - 1; i >= 0; i--) {
                SkillsToolWindowPanel.this.tree.collapseRow(i);
            }
        }
    }

    /**
     * 右键菜单基类: 仅当选中节点是 skill 时启用.
     */
    private abstract class SelectedSkillAction extends AnAction {

        SelectedSkillAction(@NotNull String titleKey, @NotNull String descKey) {
            super(
                SkillsJarsHelperBundle.messagePointer(titleKey),
                SkillsJarsHelperBundle.messagePointer(descKey),
                (javax.swing.Icon) null
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(SkillsToolWindowPanel.this.selectedSkill() != null);
        }
    }

    /**
     * 右键: Open SKILL.md.
     */
    private final class OpenSkillMdAction extends SelectedSkillAction {

        OpenSkillMdAction() {
            super("toolwindow.action.openSkillMd.title", "toolwindow.action.openSkillMd.description");
            this.getTemplatePresentation().setIcon(SkillsJarsHelperIcons.SKILLSJARS_HELPER_16);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            SkillsToolWindowPanel.this.openSelected();
        }
    }

    /**
     * 右键: Copy Skill Name.
     */
    private final class CopySkillNameAction extends SelectedSkillAction {

        CopySkillNameAction() {
            super("toolwindow.action.copySkillName.title", "toolwindow.action.copySkillName.description");
            this.getTemplatePresentation().setIcon(AllIcons.Actions.Copy);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            SkillsTreeModel.SkillNode node = SkillsToolWindowPanel.this.selectedSkill();
            if (node != null) {
                copyToClipboard(node.skill().getName());
            }
        }
    }

    /**
     * 右键: Copy Coordinate. 选中 artifact 或 skill 都可触发, 都复制 jar 的坐标.
     */
    private final class CopyCoordinateAction extends AnAction {

        CopyCoordinateAction() {
            super(
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.copyCoordinate.title"),
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.copyCoordinate.description"),
                AllIcons.Actions.Copy
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(SkillsToolWindowPanel.this.selectedArtifact() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            SkillJarArtifact artifact = SkillsToolWindowPanel.this.selectedArtifact();
            if (artifact != null) {
                copyToClipboard(artifact.getCoordinate().toCoordinateString());
            }
        }
    }

    /**
     * 右键: Open Source Jar. 在编辑器中打开 jar 根, 让 IDEA 的 jar 浏览器接管.
     */
    private final class OpenSourceJarAction extends AnAction {

        OpenSourceJarAction() {
            super(
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.openJar.title"),
                SkillsJarsHelperBundle.messagePointer("toolwindow.action.openJar.description"),
                AllIcons.Nodes.PpLib
            );
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(SkillsToolWindowPanel.this.selectedArtifact() != null);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            SkillJarArtifact artifact = SkillsToolWindowPanel.this.selectedArtifact();
            if (artifact == null) {
                return;
            }
            VirtualFile jar = artifact.getJarFile();
            VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jar);
            FileEditorManager.getInstance(SkillsToolWindowPanel.this.project)
                .openFile(jarRoot != null ? jarRoot : jar, true);
        }
    }
}
