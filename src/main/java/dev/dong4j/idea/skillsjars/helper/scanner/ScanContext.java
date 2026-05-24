package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 扫描上下文.
 *
 * <p>把扫描器需要的 IDE 入口集中起来, 避免接口随时间增长. 当前只有 {@link Project} 和可选的
 * {@link ProgressIndicator}, 后续可以扩展配置项 (例如忽略列表) 而不破坏接口.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ScanContext {

    @NotNull
    private final Project project;

    @Nullable
    private final ProgressIndicator indicator;

    /**
     * 构造扫描上下文.
     *
     * @param project   当前项目
     * @param indicator 进度指示器, 可为 null
     */
    public ScanContext(@NotNull Project project, @Nullable ProgressIndicator indicator) {
        this.project = project;
        this.indicator = indicator;
    }

    @NotNull
    public Project getProject() {
        return this.project;
    }

    @Nullable
    public ProgressIndicator getIndicator() {
        return this.indicator;
    }

    /**
     * 检查取消标志, 如果已取消则抛出 {@code ProcessCanceledException}.
     *
     * <p>扫描器在循环里调用此方法即可与 IDEA 的取消机制集成.</p>
     */
    public void checkCanceled() {
        if (this.indicator != null) {
            this.indicator.checkCanceled();
        }
    }
}
