package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

import org.jetbrains.annotations.NotNull;

/**
 * Agent Skills Tool Window 工厂.
 *
 * <p>由 {@code plugin.xml} 中 {@code <toolWindow factoryClass="...SkillsToolWindowFactory"/>}
 * 注册. 实现 {@link DumbAware} 让用户在索引未完成时也能打开 Tool Window, 但首次刷新会等待索引就绪.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillsToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        SkillsToolWindowPanel panel = new SkillsToolWindowPanel(project);
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        Disposer.register(toolWindow.getDisposable(), panel);
        toolWindow.getContentManager().addContent(content);
    }
}
