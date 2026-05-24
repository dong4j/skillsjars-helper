package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 单次 skill 导出操作的执行结果.
 *
 * <p>设计成 sealed-like 的标准结果体: {@link #isSuccess()} 区分成功 / 失败,
 * 成功时 {@link #getFinalPath()} 指向落盘目录, 失败时 {@link #getErrorMessage()}
 * 给出原因. 同时携带回执 {@link InstallationStatus} 让 UI 能直接展示 "已升级到 vX.Y"
 * 等场景化文案.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExportResult {

    private final boolean success;

    @NotNull
    private final InstallationStatus status;

    @Nullable
    private final Path finalPath;

    private final int filesWritten;

    @Nullable
    private final String errorMessage;

    private ExportResult(boolean success,
                         @NotNull InstallationStatus status,
                         @Nullable Path finalPath,
                         int filesWritten,
                         @Nullable String errorMessage) {
        this.success = success;
        this.status = status;
        this.finalPath = finalPath;
        this.filesWritten = filesWritten;
        this.errorMessage = errorMessage;
    }

    /** 成功导出. */
    @NotNull
    public static ExportResult success(@NotNull InstallationStatus status,
                                       @NotNull Path finalPath,
                                       int filesWritten) {
        return new ExportResult(true, status, finalPath, filesWritten, null);
    }

    /** 由于 UP_TO_DATE 跳过写入, 仍视为成功. */
    @NotNull
    public static ExportResult skipped(@NotNull Path existingPath) {
        return new ExportResult(true, InstallationStatus.UP_TO_DATE, existingPath, 0, null);
    }

    /** 用户取消 / 冲突拒绝 / IO 异常等情况. */
    @NotNull
    public static ExportResult failure(@NotNull InstallationStatus status, @NotNull String message) {
        return new ExportResult(false, status, null, 0, message);
    }

    public boolean isSuccess() {
        return this.success;
    }

    @NotNull
    public InstallationStatus getStatus() {
        return this.status;
    }

    @Nullable
    public Path getFinalPath() {
        return this.finalPath;
    }

    public int getFilesWritten() {
        return this.filesWritten;
    }

    @Nullable
    public String getErrorMessage() {
        return this.errorMessage;
    }
}
