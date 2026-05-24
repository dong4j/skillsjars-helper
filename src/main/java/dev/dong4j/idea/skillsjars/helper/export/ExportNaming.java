package dev.dong4j.idea.skillsjars.helper.export;

import org.jetbrains.annotations.NotNull;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;

/**
 * 导出目录命名规则.
 *
 * <p>不沿用 SkillsJars 官方扁平化前缀 {@code skillsjars__org__repo__skill}, 因为:</p>
 * <ul>
 *   <li>Agent Skills 规范 + 主流 code assistant 都期望目录名等于 frontmatter 的
 *       {@code name} (参考 skillsjars-maven-plugin issue #4 / PR #5).</li>
 *   <li>v0.0.7 官方加了 {@code useSkillsNameAsDirectory=true} 的可选行为, 但维护者
 *       (jamesward) 自己也明确指出该行为存在同名冲突的副作用. 我们自己的插件优势就是
 *       有完整 IDE 上下文, 可以处理这个副作用. </li>
 * </ul>
 *
 * <p>规则:</p>
 * <ol>
 *   <li>默认目录名 = frontmatter 的 {@code name}.</li>
 *   <li>frontmatter 没给 {@code name} (parser 已 fallback 到 jar root 末段), 直接用
 *       descriptor 的 {@code name}.</li>
 *   <li>同名冲突 (DUPLICATE_NAME) 时, fallback 命名为
 *       {@code <name>__<artifactId>}, 用 {@code __} 而非 {@code -} 是为了和 skill
 *       名内常出现的 {@code -} 区分; 没有 artifactId 时退化为 {@code <name>__source}.</li>
 *   <li>所有目录名都做合法性清理: 替换文件系统不允许的字符为 {@code _}, 防止 Windows /
 *       macOS / Linux 之间命名差异.</li>
 * </ol>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExportNaming {

    /** 文件系统不允许或语义上要避免的字符, 出现时替换为 {@code _}. */
    private static final String INVALID_CHARS_REGEX = "[\\\\/:*?\"<>|\\x00-\\x1F]";

    private ExportNaming() {
    }

    /**
     * 计算 skill 默认导出目录名.
     */
    @NotNull
    public static String defaultDirectoryName(@NotNull SkillDescriptor skill) {
        return sanitize(skill.getName());
    }

    /**
     * 同名冲突时的 fallback 目录名: {@code <name>__<artifactId>}.
     * artifactId 缺失时退化为 {@code <name>__source}.
     */
    @NotNull
    public static String duplicateFallbackDirectoryName(@NotNull SkillDescriptor skill,
                                                        @NotNull SkillCoordinate coordinate) {
        String suffix = coordinate.getArtifactId();
        if (suffix == null || suffix.isBlank()) {
            suffix = "source";
        }
        return sanitize(skill.getName()) + "__" + sanitize(suffix);
    }

    /**
     * 把潜在的非法字符替换为 {@code _}, 并去掉首尾空白.
     *
     * <p>不改变大小写; 不裁短长度 (一些 skill 名故意很长用作可读性). </p>
     */
    @NotNull
    public static String sanitize(@NotNull String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "skill";
        }
        return trimmed.replaceAll(INVALID_CHARS_REGEX, "_");
    }
}
