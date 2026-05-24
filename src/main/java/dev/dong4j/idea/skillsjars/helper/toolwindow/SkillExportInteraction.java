package dev.dong4j.idea.skillsjars.helper.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportResult;
import dev.dong4j.idea.skillsjars.helper.api.model.InstallationStatus;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;
import dev.dong4j.idea.skillsjars.helper.export.ExportNaming;
import dev.dong4j.idea.skillsjars.helper.export.TargetDirectoryDetector;
import dev.dong4j.idea.skillsjars.helper.util.NotificationUtil;
import dev.dong4j.idea.skillsjars.helper.util.SkillsJarsHelperBundle;

/**
 * 把"导出"流程包装成一个可以从右键菜单直接调用的交互层.
 *
 * <p>本类只负责 UI/对话框交互 + 调用 {@link SkillExportService}, 不持有任何业务规则;
 * 业务规则仍然在 export 包里. 抽出来是为了让 ToolWindowPanel 更聚焦于树和右键菜单本身,
 * 同时让 6 状态的交互逻辑能集中在一处, 便于未来调整文案/按钮顺序.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
final class SkillExportInteraction {

    @NotNull
    private final Project project;

    SkillExportInteraction(@NotNull Project project) {
        this.project = project;
    }

    /**
     * 导出单个 skill 到指定 Agent 目录, 由 UI 在右键菜单点击后调用.
     */
    void exportSingle(@NotNull SkillJarArtifact artifact,
                      @NotNull SkillDescriptor skill,
                      @NotNull SkillTargetDirectory target) {
        SkillExportService service = SkillExportService.getInstance(this.project);
        ExportPlan plan = service.planExport(artifact, skill, target);
        ExportPlan finalPlan = this.resolvePlanWithUserInput(plan);
        if (finalPlan == null) {
            return;
        }
        if (finalPlan.getStatus() == InstallationStatus.UP_TO_DATE) {
            this.notifyUpToDate(finalPlan);
            return;
        }
        this.runInBackground(finalPlan, service);
    }

    /**
     * 导出整个 artifact 下的所有 skill 到指定 Agent 目录.
     *
     * <p>遇到 DUPLICATE_NAME / FOREIGN / LOCALLY_MODIFIED 的 skill 都会逐个询问;
     * 用户在某一项点取消, 只跳过该项, 不中断整个批次.</p>
     */
    void exportArtifact(@NotNull SkillJarArtifact artifact,
                        @NotNull SkillTargetDirectory target) {
        SkillExportService service = SkillExportService.getInstance(this.project);
        ProgressManager.getInstance().run(new Task.Backgroundable(
            this.project,
            SkillsJarsHelperBundle.message("toolwindow.export.progress.batch", artifact.getCoordinate().toCoordinateString())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                int success = 0;
                int skipped = 0;
                int failed = 0;
                int total = artifact.getSkills().size();
                int idx = 0;
                for (SkillDescriptor skill : artifact.getSkills()) {
                    indicator.setFraction((double) idx++ / total);
                    indicator.setText(skill.getName());
                    ExportPlan plan = service.planExport(artifact, skill, target);
                    ExportPlan finalPlan = SkillExportInteraction.this.resolvePlanOnEdt(plan);
                    if (finalPlan == null) {
                        skipped++;
                        continue;
                    }
                    if (finalPlan.getStatus() == InstallationStatus.UP_TO_DATE) {
                        skipped++;
                        continue;
                    }
                    ExportResult result = service.execute(finalPlan);
                    if (result.isSuccess()) {
                        success++;
                    } else {
                        failed++;
                    }
                }
                NotificationUtil.showInfo(
                    SkillExportInteraction.this.project,
                    SkillsJarsHelperBundle.message(
                        "toolwindow.export.batchResult",
                        artifact.getCoordinate().toCoordinateString(),
                        target.getDisplayName(),
                        success, skipped, failed)
                );
            }
        });
    }

    /**
     * 弹文件选择器让用户挑一个目录, 包装成 SkillTargetDirectory.
     */
    @Nullable
    SkillTargetDirectory askCustomDirectory() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle(SkillsJarsHelperBundle.message("toolwindow.export.custom.title"))
            .withDescription(SkillsJarsHelperBundle.message("toolwindow.export.custom.description"));
        VirtualFile chosen = FileChooser.chooseFile(descriptor, this.project, null);
        if (chosen == null) {
            return null;
        }
        return TargetDirectoryDetector.customTarget(Path.of(chosen.getPath()));
    }

    // ────────────── 状态分发 ──────────────

    /**
     * 根据 plan 状态决定是否需要弹窗、需要时弹什么. 返回 null 表示用户取消.
     *
     * <p>必须在 EDT 调用, 否则 {@link Messages} 会抛.</p>
     */
    @Nullable
    private ExportPlan resolvePlanWithUserInput(@NotNull ExportPlan plan) {
        return switch (plan.getStatus()) {
            case NEW, OUTDATED, UP_TO_DATE -> plan;
            case LOCALLY_MODIFIED -> this.confirmYesNo(
                SkillsJarsHelperBundle.message("toolwindow.export.confirm.localModified.title"),
                SkillsJarsHelperBundle.message(
                    "toolwindow.export.confirm.localModified.message",
                    plan.getTargetSkillRoot())
            ) ? plan : null;
            case FOREIGN -> this.confirmYesNo(
                SkillsJarsHelperBundle.message("toolwindow.export.confirm.foreign.title"),
                SkillsJarsHelperBundle.message(
                    "toolwindow.export.confirm.foreign.message",
                    plan.getTargetSkillRoot())
            ) ? plan : null;
            case DUPLICATE_NAME -> this.resolveDuplicateName(plan);
        };
    }

    /**
     * 后台线程版本的 plan 决议 — 用 invokeAndWait 切到 EDT.
     */
    @Nullable
    private ExportPlan resolvePlanOnEdt(@NotNull ExportPlan plan) {
        ExportPlan[] holder = new ExportPlan[1];
        ApplicationManager.getApplication().invokeAndWait(() -> holder[0] = this.resolvePlanWithUserInput(plan));
        return holder[0];
    }

    /**
     * DUPLICATE_NAME 的三选项决议: 覆盖原 skill / 改用 fallback 名 / 取消.
     */
    @Nullable
    private ExportPlan resolveDuplicateName(@NotNull ExportPlan plan) {
        String overwrite = SkillsJarsHelperBundle.message("toolwindow.export.duplicate.button.overwrite");
        String suffix = SkillsJarsHelperBundle.message("toolwindow.export.duplicate.button.suffix");
        String cancel = SkillsJarsHelperBundle.message("toolwindow.export.duplicate.button.cancel");
        int choice = Messages.showDialog(
            this.project,
            SkillsJarsHelperBundle.message(
                "toolwindow.export.duplicate.message",
                plan.getSkill().getName(),
                plan.getConflictingArtifactCoordinate(),
                plan.getArtifact().getCoordinate().toCoordinateString()),
            SkillsJarsHelperBundle.message("toolwindow.export.duplicate.title"),
            new String[]{overwrite, suffix, cancel},
            0,
            Messages.getQuestionIcon()
        );
        return switch (choice) {
            case 0 -> plan;
            case 1 -> plan.withTargetDirectoryName(
                ExportNaming.duplicateFallbackDirectoryName(plan.getSkill(), plan.getArtifact().getCoordinate()));
            default -> null;
        };
    }

    // ────────────── 执行 ──────────────

    private void runInBackground(@NotNull ExportPlan plan, @NotNull SkillExportService service) {
        ProgressManager.getInstance().run(new Task.Backgroundable(
            this.project,
            SkillsJarsHelperBundle.message("toolwindow.export.progress.single", plan.getSkill().getName())) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                ExportResult result = service.execute(plan);
                SkillExportInteraction.this.notifyResult(plan, result);
            }
        });
    }

    private void notifyResult(@NotNull ExportPlan plan, @NotNull ExportResult result) {
        if (!result.isSuccess()) {
            NotificationUtil.showError(
                this.project,
                SkillsJarsHelperBundle.message(
                    "toolwindow.export.notification.failed",
                    plan.getSkill().getName(),
                    result.getErrorMessage() == null ? "" : result.getErrorMessage())
            );
            return;
        }
        String key = switch (plan.getStatus()) {
            case NEW -> "toolwindow.export.notification.installed";
            case OUTDATED -> "toolwindow.export.notification.upgraded";
            case LOCALLY_MODIFIED -> "toolwindow.export.notification.overwroteLocal";
            case FOREIGN -> "toolwindow.export.notification.overwroteForeign";
            case DUPLICATE_NAME -> "toolwindow.export.notification.duplicateResolved";
            case UP_TO_DATE -> "toolwindow.export.notification.upToDate";
        };
        NotificationUtil.showInfo(
            this.project,
            SkillsJarsHelperBundle.message(key, plan.getSkill().getName(), plan.getTargetSkillRoot())
        );
    }

    private void notifyUpToDate(@NotNull ExportPlan plan) {
        NotificationUtil.showInfo(
            this.project,
            SkillsJarsHelperBundle.message(
                "toolwindow.export.notification.upToDate",
                plan.getSkill().getName(),
                plan.getTargetSkillRoot())
        );
    }

    // ────────────── 通用 ──────────────

    private boolean confirmYesNo(@NotNull String title, @NotNull String message) {
        int answer = Messages.showYesNoDialog(this.project, message, title, Messages.getQuestionIcon());
        return answer == Messages.YES;
    }
}
