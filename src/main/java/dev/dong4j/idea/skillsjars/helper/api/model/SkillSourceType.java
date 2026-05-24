package dev.dong4j.idea.skillsjars.helper.api.model;

/**
 * Skill 来源类型枚举
 *
 * <p>用于标识一个 {@code SkillJarArtifact} 是从哪种依赖通道扫描出来的, 是 Skill 检索结果分组、
 * 风险判定、导出策略等下游逻辑的关键维度.</p>
 *
 * <p>该枚举属于公共 API, 第三方插件可以基于此值做条件过滤, 因此一旦发布就不能随意删除已有项;
 * 后续新增项必须放在末尾, 避免序列化兼容性问题.</p>
 *
 * <p>说明:
 * <ul>
 *   <li>{@link #MAVEN_DEPENDENCY}: 项目 {@code <dependencies>} 中的 Jar (一期主目标).</li>
 *   <li>{@link #MAVEN_PLUGIN_DEPENDENCY}: {@code skillsjars-maven-plugin} 的 {@code <dependencies>} (一期重要补充).</li>
 *   <li>{@link #GRADLE_DEPENDENCY}: Gradle {@code dependencies} 块, 占位项, 二期实现.</li>
 *   <li>{@link #PROJECT_OUTPUT}: 当前模块产物中的 Skill, 暂未支持.</li>
 *   <li>{@link #EXTERNAL_LIBRARY}: IDEA External Libraries 中的非 Maven/Gradle 来源.</li>
 *   <li>{@link #LOCAL_JAR}: 用户手动添加的本地 Jar.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public enum SkillSourceType {

    /** Maven 普通依赖. */
    MAVEN_DEPENDENCY,

    /** Maven 插件依赖, 即 {@code skillsjars-maven-plugin} 自身 {@code <dependencies>} 下的 Jar. */
    MAVEN_PLUGIN_DEPENDENCY,

    /** Gradle 普通依赖, 二期实现. */
    GRADLE_DEPENDENCY,

    /** 当前模块产物 (output / classes 目录). */
    PROJECT_OUTPUT,

    /** IDEA External Libraries 中无法识别为 Maven/Gradle 的来源. */
    EXTERNAL_LIBRARY,

    /** 用户手动选择的本地 Jar 文件. */
    LOCAL_JAR
}
