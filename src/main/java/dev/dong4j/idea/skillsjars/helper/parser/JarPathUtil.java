package dev.dong4j.idea.skillsjars.helper.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Jar 路径辅助工具.
 *
 * <p>IDEA {@code VirtualFile.getPath()} 在 Jar 内条目上返回形如 {@code /foo/bar.jar!/META-INF/...}
 * 的复合路径; 用 {@code java.util.jar.JarFile} 打开 jar 之前必须先剥掉 {@code !/} 后缀及其后的 entry
 * 路径. 这段逻辑在 {@code parser} / {@code export} 两层都需要, 集中到本类避免在多处复刻同一段
 * {@code indexOf("!/")} + {@code substring} 代码 (历史上曾出现过三处, 重构后统一收敛). </p>
 *
 * <p>本类是 package-private + 通过 {@code public} 工具方法暴露给同包外的少数协作类 (export 包).
 * 不属于 {@code api/} 公共契约, 第三方插件不应依赖.</p>
 *
 * <p>所有方法纯函数, 线程安全.</p>
 *
 * @author dong4j
 * @since 1.0.0
 */
public final class JarPathUtil {

    /** Jar 协议的内部分隔符. */
    private static final String JAR_SEPARATOR = "!/";

    private JarPathUtil() {
    }

    /**
     * 把 IDEA 风格的 jar 路径剥成本地 jar 文件路径字符串.
     *
     * <p>示例:</p>
     * <pre>
     *   /foo/bar.jar!/META-INF/skills/x/SKILL.md  ->  /foo/bar.jar
     *   /foo/bar.jar                              ->  /foo/bar.jar  (原样返回)
     * </pre>
     *
     * @param rawPath 任意 jar 相关路径, 不为 null
     * @return 剥掉 {@code !/} 之后的字符串
     */
    @NotNull
    public static String stripJarSuffix(@NotNull String rawPath) {
        int sep = rawPath.indexOf(JAR_SEPARATOR);
        return sep >= 0 ? rawPath.substring(0, sep) : rawPath;
    }

    /**
     * {@link #stripJarSuffix(String)} 的便捷重载, 直接返回 {@link Path}.
     *
     * <p>构造 {@link Path} 失败时返回 null (与 {@code Path.of} 的契约一致, 但不抛
     * {@link java.nio.file.InvalidPathException}, 调用方按 null 兜底即可).</p>
     *
     * @param rawPath 任意 jar 相关路径
     * @return 本地 jar 的 nio Path; 构造失败返回 null
     */
    @Nullable
    public static Path toLocalJarPath(@NotNull String rawPath) {
        try {
            return Path.of(stripJarSuffix(rawPath));
        } catch (Exception e) {
            return null;
        }
    }
}
