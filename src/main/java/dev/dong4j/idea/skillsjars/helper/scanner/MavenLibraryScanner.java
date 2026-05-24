package dev.dong4j.idea.skillsjars.helper.scanner;

import org.jetbrains.annotations.NotNull;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * Maven 普通依赖扫描器.
 *
 * <p>识别 IDEA 中以 {@code "Maven: "} 开头的 Library, 这是 Maven 插件导入项目时给每个 jar
 * 依赖打的标签. 该扫描器覆盖以下使用方式:</p>
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>com.skillsjars</groupId>
 *     <artifactId>example-skill</artifactId>
 *     <version>1.0.0</version>
 * </dependency>
 * }</pre>
 *
 * <p>它<strong>不</strong>覆盖 {@code skillsjars-maven-plugin} 的 {@code <dependencies>} 块,
 * 因为那部分依赖不会出现在项目 classpath, 需要通过 Maven 插件 API 单独拉取, 见
 * {@code MavenPluginDependencyScanner}.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MavenLibraryScanner extends AbstractLibraryScanner {

    /** IDEA 给 Maven 依赖打的标签前缀. */
    private static final String LIBRARY_NAME_PREFIX = "Maven: ";

    @Override
    @NotNull
    public String getDisplayName() {
        return "Maven Dependencies";
    }

    @Override
    @NotNull
    protected String getLibraryNamePrefix() {
        return LIBRARY_NAME_PREFIX;
    }

    @Override
    @NotNull
    protected SkillSourceType getSourceType() {
        return SkillSourceType.MAVEN_DEPENDENCY;
    }
}
