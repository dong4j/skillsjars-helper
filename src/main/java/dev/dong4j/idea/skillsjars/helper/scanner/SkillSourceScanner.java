package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.extensions.ExtensionPointName;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * SkillsJars 候选 Jar 扫描器扩展点.
 *
 * <p>所有扫描器 (Maven 普通依赖、Maven plugin dependencies、未来的 Gradle、外部 Library 等)
 * 都通过这个扩展点接入. 这样可以做到:</p>
 * <ul>
 *   <li>一期只注册 Maven 相关扫描器, 二期再补充 Gradle 扫描器, 不需要修改协调层.</li>
 *   <li>第三方插件可以扩展自己的来源 (例如企业自研构建工具).</li>
 *   <li>每个扫描器独立判断是否适用于当前项目, 适用时才执行扫描, 减少无谓 I/O.</li>
 * </ul>
 *
 * <p>扩展点采用 IDEA 标准方式声明: {@code dev.dong4j.idea.skillsjars.helper.skillSourceScanner}.</p>
 *
 * <p>实现注意:</p>
 * <ul>
 *   <li>扫描器应只产出候选 Jar; 不要尝试自己读 SKILL.md, 解析交给协调层 + parser.</li>
 *   <li>扫描方法运行在后台线程, 但不要长时间阻塞 EDT 或在循环内忘记调用 {@link ScanContext#checkCanceled()}.</li>
 *   <li>同一个 Jar 多次出现是正常的, 协调层会按 jar 路径去重.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SkillSourceScanner {

    /** IDEA 扩展点名称. */
    ExtensionPointName<SkillSourceScanner> EP_NAME =
        ExtensionPointName.create("dev.dong4j.idea.skillsjars.helper.skillSourceScanner");

    /**
     * 扫描器的可读名称, 用于日志和调试.
     *
     * @return 名称
     */
    @NotNull
    String getDisplayName();

    /**
     * 判断当前项目是否适用. 不适用时 {@link #scan(ScanContext)} 不会被调用.
     *
     * @param context 扫描上下文
     * @return 适用返回 true
     */
    boolean isApplicable(@NotNull ScanContext context);

    /**
     * 执行扫描.
     *
     * @param context 扫描上下文
     * @return 候选 Jar 列表; 没有结果时返回空列表
     */
    @NotNull
    List<SkillJarSource> scan(@NotNull ScanContext context);
}
