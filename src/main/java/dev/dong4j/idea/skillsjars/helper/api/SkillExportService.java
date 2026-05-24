package dev.dong4j.idea.skillsjars.helper.api;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportResult;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;

/**
 * SkillsJars Helper 对外暴露的导出能力总入口.
 *
 * <p>项目级 service. 与 {@link SkillRegistry} 形成 "发现 / 导出" 双服务对称, 第三方
 * 插件可以通过此接口在不依赖 ToolWindow 的情况下完成同样的导出动作.</p>
 *
 * <p>典型调用顺序:</p>
 * <ol>
 *   <li>{@link #detectTargets(Project)} 拿到预设 Agent 目录候选.</li>
 *   <li>{@link #planExport(SkillJarArtifact, SkillDescriptor, SkillTargetDirectory)}
 *       计算出冲突状态、文件清单与落盘子目录名.</li>
 *   <li>UI 根据 {@link ExportPlan#getStatus()} 决定是否需要用户确认.</li>
 *   <li>用户确认后调用 {@link #execute(ExportPlan)}.</li>
 * </ol>
 *
 * <p>本接口承诺所有方法都是线程安全的; 但 {@code execute} 内部会触发文件 IO + VFS
 * refresh, 调用方应在合适的线程上下文 (后台 + invokeLater 或 WriteAction) 中调用.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.PROJECT)
public interface SkillExportService {

    /**
     * 获取项目级 service 实例.
     */
    @NotNull
    static SkillExportService getInstance(@NotNull Project project) {
        return project.getService(SkillExportService.class);
    }

    /**
     * 探测当前项目的可用 Agent 目录.
     *
     * <p>返回 {@link SkillTargetDirectory#PRESET_AGENT_IDS} 中所有预设 Agent 的目录候选,
     * 路径默认相对项目根; 不论目录是否真实存在都会返回, 由 UI 自行判断展示与否.</p>
     *
     * @param project 当前项目
     * @return 不可变候选列表; 项目无 basePath 时返回空列表
     */
    @NotNull
    List<SkillTargetDirectory> detectTargets(@NotNull Project project);

    /**
     * 计算单个 skill 导出到给定目录的执行计划.
     *
     * <p>不会触碰磁盘以外的状态, 不写入任何文件. 仅做读取 + 哈希比对; 调用方拿到计划后
     * 自行决定是否调用 {@link #execute(ExportPlan)}.</p>
     *
     * @param artifact        skill 所属的 jar
     * @param skill           待导出 skill
     * @param targetDirectory Agent 目标目录
     * @return 不可变执行计划
     */
    @NotNull
    ExportPlan planExport(@NotNull SkillJarArtifact artifact,
                          @NotNull SkillDescriptor skill,
                          @NotNull SkillTargetDirectory targetDirectory);

    /**
     * 真正执行导出.
     *
     * <p>实现要点: 临时目录写入 → 必要时删除原目标 → rename 到目标 → 写 manifest →
     * VFS refresh → 通知 InstallationRegistry 更新状态. 单个 skill 失败不影响其它
     * 调用; 调用方需要批量导出时自行循环.</p>
     *
     * @param plan 通过 {@link #planExport} 拿到 (并可能通过
     *             {@link ExportPlan#withTargetDirectoryName(String)} 改名) 的计划
     * @return 执行结果
     */
    @NotNull
    ExportResult execute(@NotNull ExportPlan plan);

    /**
     * 订阅安装状态变化事件 (导出 / 删除 / 状态重算).
     *
     * <p>事件强制在 EDT 派发, 保证监听器侧可以直接更新 Swing 组件. 返回的
     * {@link Disposable} 必须由调用方妥善关闭, 否则会内存泄漏.</p>
     *
     * @param listener 监听器
     * @return 订阅句柄
     */
    @NotNull
    Disposable addInstallationListener(@NotNull SkillInstallationListener listener);
}
