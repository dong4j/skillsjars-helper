package dev.dong4j.idea.skillsjars.helper.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import dev.dong4j.idea.skillsjars.helper.api.SkillRegistry;
import dev.dong4j.idea.skillsjars.helper.api.SkillRegistryListener;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.parser.SkillJarParser;
import dev.dong4j.idea.skillsjars.helper.scanner.ScanContext;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillJarSource;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner;

/**
 * SkillRegistry 的项目级实现.
 *
 * <p>职责:</p>
 * <ul>
 *   <li>调度所有 {@link SkillSourceScanner}, 把候选 Jar 收集起来.</li>
 *   <li>调用 {@link SkillJarParser} 把候选 Jar 解析成 {@link SkillJarArtifact}.</li>
 *   <li>持有最新的快照, 并在刷新结束时通知监听器.</li>
 * </ul>
 *
 * <p>线程模型:</p>
 * <ul>
 *   <li>{@link #refresh()} 在后台线程通过 {@code ProgressManager.run(Backgroundable)} 执行, 不阻塞 EDT.</li>
 *   <li>快照通过 {@link AtomicReference} 持有, 读取无锁.</li>
 *   <li>监听器派发在 EDT 上, 与 IDEA 通用约定保持一致.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class SkillRegistryService implements SkillRegistry, Disposable {

    private static final Logger LOG = Logger.getInstance(SkillRegistryService.class);

    @NotNull
    private final Project project;

    @NotNull
    private final SkillJarParser parser = new SkillJarParser();

    @NotNull
    private final AtomicReference<List<SkillJarArtifact>> snapshot =
        new AtomicReference<>(Collections.emptyList());

    @NotNull
    private final List<SkillRegistryListener> listeners = new CopyOnWriteArrayList<>();

    /** 上次刷新任务的 monitor, 仅用于 {@link #awaitRefresh()} 等测试同步场景. */
    @NotNull
    private final Object refreshLock = new Object();

    /** 是否有刷新正在进行中. */
    private volatile boolean refreshing = false;

    /**
     * 构造函数.
     *
     * @param project 当前项目
     */
    public SkillRegistryService(@NotNull Project project) {
        this.project = project;
    }

    @Override
    @NotNull
    public List<SkillJarArtifact> getArtifacts() {
        return this.snapshot.get();
    }

    @Override
    @NotNull
    public List<SkillDescriptor> getSkills() {
        List<SkillJarArtifact> artifacts = this.snapshot.get();
        List<SkillDescriptor> result = new ArrayList<>();
        for (SkillJarArtifact artifact : artifacts) {
            result.addAll(artifact.getSkills());
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    @NotNull
    public List<SkillDescriptor> findByName(@NotNull String name) {
        List<SkillDescriptor> result = new ArrayList<>();
        for (SkillJarArtifact artifact : this.snapshot.get()) {
            for (SkillDescriptor skill : artifact.getSkills()) {
                if (name.equals(skill.getName())) {
                    result.add(skill);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    @NotNull
    public List<SkillJarArtifact> findByCoordinate(@NotNull String coordinate) {
        SkillCoordinate target = parseQuery(coordinate);
        List<SkillJarArtifact> result = new ArrayList<>();
        for (SkillJarArtifact artifact : this.snapshot.get()) {
            if (matches(target, artifact.getCoordinate())) {
                result.add(artifact);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void refresh() {
        if (this.project.isDisposed()) {
            return;
        }
        this.refreshing = true;
        ProgressManager.getInstance().run(new Task.Backgroundable(this.project, "Scanning SkillsJars", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<SkillJarArtifact> next = SkillRegistryService.this.doRefresh(indicator);
                    SkillRegistryService.this.snapshot.set(next);
                    SkillRegistryService.this.fireRefreshed(next);
                } catch (ProcessCanceledException ignored) {
                    // 用户取消, 保持上一次快照
                } catch (Throwable t) {
                    LOG.warn("SkillsJars refresh failed", t);
                } finally {
                    synchronized (SkillRegistryService.this.refreshLock) {
                        SkillRegistryService.this.refreshing = false;
                        SkillRegistryService.this.refreshLock.notifyAll();
                    }
                }
            }
        });
    }

    @Override
    public void awaitRefresh() {
        synchronized (this.refreshLock) {
            while (this.refreshing) {
                try {
                    this.refreshLock.wait(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    @NotNull
    public Disposable addListener(@NotNull SkillRegistryListener listener) {
        this.listeners.add(listener);
        Disposable disposable = () -> this.listeners.remove(listener);
        Disposer.register(this, disposable);
        return disposable;
    }

    @Override
    public void dispose() {
        this.listeners.clear();
        this.snapshot.set(Collections.emptyList());
    }

    /**
     * 同步执行扫描 + 解析, 仅供刷新任务和测试使用.
     *
     * @param indicator 进度指示器
     * @return 新的快照
     */
    @NotNull
    List<SkillJarArtifact> doRefresh(@NotNull ProgressIndicator indicator) {
        ScanContext context = new ScanContext(this.project, indicator);
        Map<String, SkillJarSource> dedup = new HashMap<>();

        for (SkillSourceScanner scanner : SkillSourceScanner.EP_NAME.getExtensionList()) {
            indicator.checkCanceled();
            if (!scanner.isApplicable(context)) {
                continue;
            }
            indicator.setText2("Scanning: " + scanner.getDisplayName());
            try {
                for (SkillJarSource source : scanner.scan(context)) {
                    // 同一 jar 路径只保留第一个扫描器的结果, 避免来源被覆盖.
                    // 后续可以加 sourceType 优先级排序, 一期保持简单.
                    dedup.putIfAbsent(source.getJarFile().getPath(), source);
                }
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Throwable t) {
                LOG.warn("Scanner failed: " + scanner.getDisplayName(), t);
            }
        }

        List<SkillJarArtifact> result = new ArrayList<>();
        for (SkillJarSource source : dedup.values()) {
            indicator.checkCanceled();
            SkillJarArtifact artifact = this.parser.parse(
                source.getJarFile(),
                source.getSourceType(),
                source.getCoordinate(),
                source.getDisplayName()
            );
            if (artifact != null) {
                result.add(artifact);
            }
        }
        // 按 sourceType 后再按 coordinate 排序, 让 UI 展示稳定
        result.sort((a, b) -> {
            int c = a.getSourceType().compareTo(b.getSourceType());
            if (c != 0) {
                return c;
            }
            return a.getCoordinate().toCoordinateString().compareTo(b.getCoordinate().toCoordinateString());
        });
        return Collections.unmodifiableList(result);
    }

    /**
     * 把变更事件派发到监听器.
     */
    private void fireRefreshed(@NotNull List<SkillJarArtifact> snapshot) {
        if (this.listeners.isEmpty()) {
            return;
        }
        Runnable dispatch = () -> {
            for (SkillRegistryListener listener : this.listeners) {
                try {
                    listener.onSkillsRefreshed(snapshot);
                } catch (Throwable t) {
                    LOG.warn("SkillRegistryListener failed", t);
                }
            }
        };
        if (ApplicationManager.getApplication().isDispatchThread()) {
            dispatch.run();
        } else {
            ApplicationManager.getApplication().invokeLater(dispatch, this.project.getDisposed());
        }
    }

    /**
     * 解析 {@code findByCoordinate} 入参为 {@link SkillCoordinate}.
     */
    @NotNull
    private static SkillCoordinate parseQuery(@NotNull String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length == 2) {
            return SkillCoordinate.of(parts[0], parts[1], null);
        }
        if (parts.length >= 3) {
            return SkillCoordinate.of(parts[0], parts[1], parts[2]);
        }
        return SkillCoordinate.unknown();
    }

    private static boolean matches(@NotNull SkillCoordinate query, @NotNull SkillCoordinate target) {
        if (query.getGroupId() != null && !query.getGroupId().equals(target.getGroupId())) {
            return false;
        }
        if (query.getArtifactId() != null && !query.getArtifactId().equals(target.getArtifactId())) {
            return false;
        }
        if (query.getVersion() != null && !query.getVersion().equals(target.getVersion())) {
            return false;
        }
        return true;
    }
}
