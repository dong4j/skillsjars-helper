package dev.dong4j.idea.skillsjars.helper.api;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

/**
 * SkillsJars 检索能力的对外接口.
 *
 * <p>这是 SkillsJars Helper 暴露给其他 IDEA 插件的主要扩展接口. 设计目标:</p>
 * <ul>
 *   <li>调用方不需要理解 Jar 扫描细节, 只需要拿到结构化的 {@link SkillJarArtifact}/{@link SkillDescriptor}.</li>
 *   <li>提供同步查询和异步刷新两种入口, 调用方可按需选择.</li>
 *   <li>变更通过 {@link SkillRegistryListener} 派发, 便于其他插件订阅 (例如在自己的 UI 中跟随刷新).</li>
 *   <li>不暴露 Tool Window / 文件系统细节, 不绑定具体构建工具.</li>
 * </ul>
 *
 * <p>典型用法:</p>
 * <pre>{@code
 * SkillRegistry registry = SkillRegistry.getInstance(project);
 * for (SkillJarArtifact artifact : registry.getArtifacts()) {
 *     for (SkillDescriptor skill : artifact.getSkills()) {
 *         // 渲染 / 推荐 / 注入上下文 ...
 *     }
 * }
 * }</pre>
 *
 * <p>线程模型: 实现方应保证 {@link #getArtifacts()} 等只读方法可以在任意线程调用; 监听器回调
 * 默认在 EDT 触发, 调用方在监听器中执行重活时应自行切换到后台线程.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SkillRegistry {

    /**
     * 获取项目级实例.
     *
     * <p>底层是 IDEA 的 {@code @Service(Service.Level.PROJECT)}, 调用方无需自己缓存.</p>
     *
     * @param project 当前项目
     * @return 注册表实例
     */
    @NotNull
    static SkillRegistry getInstance(@NotNull Project project) {
        return project.getService(SkillRegistry.class);
    }

    /**
     * 返回当前已索引的 Jar 制品列表 (只读快照).
     *
     * <p>该方法不会触发扫描; 第一次调用前如果还没有刷新过, 返回空列表. 调用方需要确保扫描动作
     * 已完成 (通过 {@link #refresh()} 或 ToolWindow 的初始化).</p>
     *
     * @return Jar 制品快照
     */
    @NotNull
    List<SkillJarArtifact> getArtifacts();

    /**
     * 返回所有 Skill 描述符的扁平列表 (只读快照).
     *
     * <p>等价于 {@code getArtifacts().stream().flatMap(a -> a.getSkills().stream())}, 但实现方可以
     * 选择更高效的实现.</p>
     *
     * @return Skill 列表
     */
    @NotNull
    List<SkillDescriptor> getSkills();

    /**
     * 按名称查找 Skill.
     *
     * <p>使用大小写敏感比较. 多个 Jar 内可能存在同名 Skill, 因此返回列表.</p>
     *
     * @param name Skill 名称
     * @return 匹配的 Skill 列表; 没有匹配时返回空列表
     */
    @NotNull
    List<SkillDescriptor> findByName(@NotNull String name);

    /**
     * 按坐标查找 Jar 制品.
     *
     * <p>{@code coordinate} 可以是 {@code groupId:artifactId} 或 {@code groupId:artifactId:version}.
     * 前者匹配所有版本, 后者精确匹配.</p>
     *
     * @param coordinate Maven 风格坐标
     * @return 匹配的 Jar 制品列表
     */
    @NotNull
    List<SkillJarArtifact> findByCoordinate(@NotNull String coordinate);

    /**
     * 异步刷新索引.
     *
     * <p>实现方应在后台线程中执行扫描, 不阻塞调用方. 刷新完成后会派发 {@link SkillRegistryListener#onSkillsRefreshed}.</p>
     */
    void refresh();

    /**
     * 同步等待当前刷新结束.
     *
     * <p>主要面向测试场景, 业务调用通常不应使用. 实现方在没有进行中的刷新时可以立即返回.</p>
     */
    void awaitRefresh();

    /**
     * 注册监听器.
     *
     * <p>返回的 {@link Disposable} 用于取消注册; 推荐通过 {@code Disposer.register(parent, disposable)}
     * 与调用方的生命周期绑定, 避免泄漏.</p>
     *
     * @param listener 监听器
     * @return 用于取消注册的 Disposable
     */
    @NotNull
    Disposable addListener(@NotNull SkillRegistryListener listener);
}
