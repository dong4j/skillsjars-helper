package dev.dong4j.idea.skillsjars.helper.api;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

/**
 * SkillRegistry 变更监听器.
 *
 * <p>第三方插件订阅扫描结果变化的入口. 当前只暴露一个事件入口, 后续可扩展更细粒度事件
 * (例如 added / removed), 但要保留默认实现避免破坏现有调用方.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
@FunctionalInterface
public interface SkillRegistryListener extends EventListener {

    /**
     * 索引刷新完成时回调.
     *
     * <p>该回调在 EDT 上派发. 调用方如果需要执行重活 (例如重绘大表), 应自行切到后台线程
     * 处理或通过 {@code invokeLater} 推后.</p>
     *
     * @param artifacts 当前最新的 Jar 制品快照
     */
    void onSkillsRefreshed(@NotNull List<SkillJarArtifact> artifacts);
}
