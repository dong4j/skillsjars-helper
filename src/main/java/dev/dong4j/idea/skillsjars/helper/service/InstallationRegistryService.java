package dev.dong4j.idea.skillsjars.helper.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.SkillInstallationListener;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;
import dev.dong4j.idea.skillsjars.helper.export.ManifestJson;
import dev.dong4j.idea.skillsjars.helper.export.ManifestSchema;
import dev.dong4j.idea.skillsjars.helper.export.TargetDirectoryDetector;

/**
 * 安装状态索引服务.
 *
 * <p>定期 (按需) 扫描所有预设 Agent 目录 (清单见 {@link SkillTargetDirectory#PRESET_AGENT_IDS}),
 * 把每个目录里包含 {@code .skillsjars-helper.json} 的子目录解析成 manifest, 形成
 * (artifact + skillRoot) → {目标 agent 列表} 的索引.</p>
 *
 * <p>UI 用此服务回答两个问题:</p>
 * <ul>
 *   <li>"这个 skill 已经装到了哪些 Agent?" → {@link #findInstalledLocations}.</li>
 *   <li>"这个 Agent 目录里现在装了哪些 skill?" → {@link #snapshotByAgent}.</li>
 * </ul>
 *
 * <p>线程模型:</p>
 * <ul>
 *   <li>{@link #refresh()} 同步执行, IO 量极小 (只读预设目录下的 manifest), 不需要后台
 *       进度条; UI 在右键展开 / 导出后调用即可.</li>
 *   <li>快照通过 {@link AtomicReference} 持有, 读取无锁.</li>
 *   <li>监听器派发在 EDT 上.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.PROJECT)
public final class InstallationRegistryService implements Disposable {

    private static final Logger LOG = Logger.getInstance(InstallationRegistryService.class);

    @NotNull
    private final Project project;

    /** key = artifact + "::" + skillRoot, value = 安装位置列表 */
    @NotNull
    private final AtomicReference<Map<String, List<InstalledLocation>>> snapshot =
        new AtomicReference<>(Collections.emptyMap());

    @NotNull
    private final List<SkillInstallationListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * 是否已完成首次"订阅 SkillExportService + 触发后台初始扫描".
     *
     * <p>Light service 的构造器禁止 {@code getService(...)} 别的依赖服务
     * (官方原文: "Other service dependencies must be acquired only when needed
     * in all corresponding methods"), 因此把这两个动作延后到首次对外方法被调用时.</p>
     */
    @NotNull
    private final AtomicBoolean subscribed = new AtomicBoolean(false);

    /**
     * Light service 构造器. <strong>不要</strong>在这里调用 {@code getService(...)}
     * 或者做任何 IO; 重活全部放在 {@link #ensureSubscribed()} 里走 lazy 路径.
     */
    public InstallationRegistryService(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static InstallationRegistryService getInstance(@NotNull Project project) {
        return project.getService(InstallationRegistryService.class);
    }

    /**
     * 重新扫描所有预设目录, 替换内存快照, 然后通知监听器.
     */
    public void refresh() {
        this.ensureSubscribed();
        Map<String, List<InstalledLocation>> next = new HashMap<>();
        List<SkillTargetDirectory> targets = TargetDirectoryDetector.detect(this.project);
        for (SkillTargetDirectory target : targets) {
            this.scanAgentDirectory(target, next);
        }
        this.snapshot.set(Map.copyOf(next));
        this.fireChanged();
    }

    /**
     * 查询某个 skill 当前装在哪些 Agent 目录下.
     *
     * @param coordinate skill 来源坐标
     * @param skillRoot  skill 在 jar 内的根路径
     * @return 不可变安装位置列表; 没有任何安装时返回空列表
     */
    @NotNull
    public List<InstalledLocation> findInstalledLocations(@NotNull SkillCoordinate coordinate,
                                                         @NotNull String skillRoot) {
        return this.findInstalledLocations(coordinate.toCoordinateString(), skillRoot);
    }

    /**
     * 同 {@link #findInstalledLocations(SkillCoordinate, String)}, 但 artifact 直接以
     * 字符串形式给出, 主要用于内部调用.
     */
    @NotNull
    public List<InstalledLocation> findInstalledLocations(@NotNull String artifactCoord,
                                                         @NotNull String skillRoot) {
        this.ensureSubscribed();
        String key = key(artifactCoord, skillRoot);
        List<InstalledLocation> list = this.snapshot.get().get(key);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * 拿到完整快照, 主要给 ToolWindow 一次性渲染所有徽标用.
     */
    @NotNull
    public Map<String, List<InstalledLocation>> snapshotByAgent() {
        this.ensureSubscribed();
        return this.snapshot.get();
    }

    /**
     * 订阅安装状态变化.
     *
     * <p>返回的 {@link Disposable} 已自动挂到 service 自身, 调用方仍应 {@code Disposer.register}
     * 到自己组件的生命周期上以求精确控制; 即使忘记, 项目关闭时 service dispose 也会一次性回收,
     * 不会留下悬挂 listener.</p>
     */
    @NotNull
    public Disposable addListener(@NotNull SkillInstallationListener listener) {
        this.ensureSubscribed();
        this.listeners.add(listener);
        Disposable disposable = () -> this.listeners.remove(listener);
        Disposer.register(this, disposable);
        return disposable;
    }

    /**
     * 给 UI 调: skill 是否安装到了某个 agent.
     */
    public boolean isInstalledIn(@NotNull SkillDescriptor skill,
                                 @NotNull SkillCoordinate coordinate,
                                 @NotNull String agentId) {
        for (InstalledLocation l : this.findInstalledLocations(coordinate, skill.getJarEntryRoot())) {
            if (l.getAgentId().equals(agentId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一次性把"订阅 SkillExportService + 触发后台初始扫描"做完.
     *
     * <p>从构造器挪到 lazy init 的原因是 light service 约束: 构造器不允许
     * {@code getService(...)} 别的服务; 同时也避免 service 初始化期间的循环引用风险.</p>
     */
    private void ensureSubscribed() {
        if (!this.subscribed.compareAndSet(false, true)) {
            return;
        }
        SkillExportService exportService = SkillExportService.getInstance(this.project);
        Disposable subscription = exportService.addInstallationListener(src -> this.refresh());
        Disposer.register(this, subscription);
        ApplicationManager.getApplication().executeOnPooledThread(this::refresh);
    }

    @Override
    public void dispose() {
        this.listeners.clear();
    }

    // ─────────────── 内部 ───────────────

    private void scanAgentDirectory(@NotNull SkillTargetDirectory target,
                                    @NotNull Map<String, List<InstalledLocation>> out) {
        Path agentRoot = target.getPath();
        if (!Files.isDirectory(agentRoot)) {
            return;
        }
        try (Stream<Path> children = Files.list(agentRoot)) {
            children.filter(Files::isDirectory).forEach(child -> {
                Path manifestPath = child.resolve(ManifestSchema.FILE_NAME);
                if (!Files.isRegularFile(manifestPath)) {
                    return;
                }
                try {
                    String content = Files.readString(manifestPath);
                    ManifestSchema manifest = ManifestJson.fromJson(content);
                    if (manifest == null || !manifest.isManagedByThisPlugin()) {
                        return;
                    }
                    String key = key(manifest.getArtifact(), manifest.getSkillRoot());
                    out.computeIfAbsent(key, k -> new ArrayList<>())
                       .add(new InstalledLocation(target, child, manifest));
                } catch (IOException e) {
                    LOG.debug("Failed to read manifest at " + manifestPath, e);
                }
            });
        } catch (IOException e) {
            LOG.debug("Failed to list " + agentRoot, e);
        }
    }

    @NotNull
    private static String key(@NotNull String artifact, @NotNull String skillRoot) {
        return artifact + "::" + skillRoot;
    }

    private void fireChanged() {
        ApplicationManager.getApplication().invokeLater(() -> {
            for (SkillInstallationListener l : this.listeners) {
                try {
                    l.onInstallationsChanged(SkillExportService.getInstance(this.project));
                } catch (Throwable t) {
                    LOG.warn("Installation listener threw", t);
                }
            }
        });
    }

    /**
     * 一个 skill 的具体安装位置.
     */
    public static final class InstalledLocation {

        @NotNull private final SkillTargetDirectory target;
        @NotNull private final Path path;
        @NotNull private final ManifestSchema manifest;

        public InstalledLocation(@NotNull SkillTargetDirectory target,
                                 @NotNull Path path,
                                 @NotNull ManifestSchema manifest) {
            this.target = target;
            this.path = path;
            this.manifest = manifest;
        }

        @NotNull
        public SkillTargetDirectory getTarget() {
            return this.target;
        }

        @NotNull
        public String getAgentId() {
            return this.target.getAgentId();
        }

        @NotNull
        public Path getPath() {
            return this.path;
        }

        @NotNull
        public ManifestSchema getManifest() {
            return this.manifest;
        }
    }
}
