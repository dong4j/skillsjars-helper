package dev.dong4j.idea.skillsjars.helper.api;

import org.jetbrains.annotations.NotNull;

/**
 * Skill 安装状态变化监听器.
 *
 * <p>{@link SkillExportService#execute} 完成后、或 {@code InstallationRegistry}
 * 在文件变更后重算状态时, 都会派发本事件. 实现端通常用它来刷新 ToolWindow 上的状态
 * 徽标.</p>
 *
 * <p>事件保证在 EDT 派发, 实现可以直接更新 Swing 组件.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
@FunctionalInterface
public interface SkillInstallationListener {

    /**
     * 安装状态变化时被调用.
     *
     * <p>事件粒度刻意保持粗: 不区分单 skill 还是批量, 监听者应该重新查询当前需要展示
     * 的状态. 这样实现端不需要维护增量, 也避免事件风暴.</p>
     */
    void onInstallationsChanged(@NotNull SkillExportService source);
}
