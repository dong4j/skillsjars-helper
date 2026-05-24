package dev.dong4j.idea.skillsjars.helper.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.SkillInstallationListener;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportResult;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;
import dev.dong4j.idea.skillsjars.helper.export.ExportExecutor;
import dev.dong4j.idea.skillsjars.helper.export.ExportPlanner;
import dev.dong4j.idea.skillsjars.helper.export.TargetDirectoryDetector;

/**
 * {@link SkillExportService} 的项目级实现.
 *
 * <p>本类是组装层: 把 {@link TargetDirectoryDetector} / {@link ExportPlanner} /
 * {@link ExportExecutor} 串成对外的简洁 API. 真正的业务规则都在 export 包内, 服务层
 * 只负责 (1) 转发调用, (2) 执行后做 VFS refresh + 通知监听器.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillExportServiceImpl implements SkillExportService, Disposable {

    private static final Logger LOG = Logger.getInstance(SkillExportServiceImpl.class);

    @NotNull
    private final Project project;

    @NotNull
    private final ExportPlanner planner = new ExportPlanner();

    @NotNull
    private final ExportExecutor executor = new ExportExecutor();

    @NotNull
    private final List<SkillInstallationListener> listeners = new CopyOnWriteArrayList<>();

    public SkillExportServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    @Override
    @NotNull
    public List<SkillTargetDirectory> detectTargets(@NotNull Project project) {
        return TargetDirectoryDetector.detect(project);
    }

    @Override
    @NotNull
    public ExportPlan planExport(@NotNull SkillJarArtifact artifact,
                                 @NotNull SkillDescriptor skill,
                                 @NotNull SkillTargetDirectory targetDirectory) {
        return this.planner.plan(artifact, skill, targetDirectory);
    }

    @Override
    @NotNull
    public ExportResult execute(@NotNull ExportPlan plan) {
        ExportResult result = this.executor.execute(plan);
        if (result.isSuccess()) {
            this.refreshVfs(plan);
            this.fireChanged();
        }
        return result;
    }

    @Override
    @NotNull
    public Disposable addInstallationListener(@NotNull SkillInstallationListener listener) {
        this.listeners.add(listener);
        // 与 SkillRegistryService 风格统一: 把订阅 disposable 挂到 service 自身 (本身
        // 也是 Disposable), 项目关闭时即使调用方忘记 dispose, 也能在 service dispose 阶段
        // 一次性回收, 避免长会话里的 listener 累积泄漏.
        Disposable disposable = () -> this.listeners.remove(listener);
        Disposer.register(this, disposable);
        return disposable;
    }

    /**
     * 让 IDEA 看见新写入的目录, 否则项目视图直到下次 sync 才显示.
     */
    private void refreshVfs(@NotNull ExportPlan plan) {
        VirtualFile parent = LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(plan.getTargetDirectory().getPath());
        if (parent != null) {
            VfsUtil.markDirtyAndRefresh(true, true, true, parent);
        }
    }

    /**
     * 在 EDT 上派发安装状态变化事件.
     */
    private void fireChanged() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (SkillInstallationListener l : this.listeners) {
                try {
                    l.onInstallationsChanged(this);
                } catch (Throwable t) {
                    LOG.warn("Installation listener threw", t);
                }
            }
        });
    }

    @Override
    public void dispose() {
        this.listeners.clear();
    }
}
