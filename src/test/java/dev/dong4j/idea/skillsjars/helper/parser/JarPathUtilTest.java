package dev.dong4j.idea.skillsjars.helper.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JarPathUtil} 单元测试.
 *
 * <p>这是 parser / export 两层共享的"剥 IDEA jar 路径尾"工具, 历史上同一段逻辑曾在三个文件
 * 里重复写过, 必须有独立测试守住语义边界 (尤其是 "不含 !/ 时原样返回").</p>
 */
class JarPathUtilTest {

    @Test
    @DisplayName("含 !/ 的 IDEA jar 路径会被剥成本地 jar 文件路径")
    void should_strip_jar_suffix_when_present() {
        assertThat(JarPathUtil.stripJarSuffix("/foo/bar.jar!/META-INF/skills/x/SKILL.md"))
            .isEqualTo("/foo/bar.jar");
        assertThat(JarPathUtil.stripJarSuffix("/foo/bar.jar!/"))
            .isEqualTo("/foo/bar.jar");
    }

    @Test
    @DisplayName("不含 !/ 的本地路径原样返回, 不会引入额外副作用")
    void should_return_as_is_when_no_jar_suffix() {
        assertThat(JarPathUtil.stripJarSuffix("/foo/bar.jar")).isEqualTo("/foo/bar.jar");
        assertThat(JarPathUtil.stripJarSuffix("relative/path.txt")).isEqualTo("relative/path.txt");
        assertThat(JarPathUtil.stripJarSuffix("")).isEqualTo("");
    }

    @Test
    @DisplayName("toLocalJarPath 返回有效 nio Path")
    void should_construct_path_from_stripped() {
        Path p = JarPathUtil.toLocalJarPath("/foo/bar.jar!/inner");
        assertThat(p).isNotNull();
        assertThat(p.toString()).isEqualTo("/foo/bar.jar");
    }

    @Test
    @DisplayName("非法路径返回 null 而非抛异常")
    void should_return_null_on_invalid_path() {
        // POSIX 平台几乎任何字符串都是合法路径; 这里只验证空串不抛
        assertThat(JarPathUtil.toLocalJarPath("")).isNotNull();
    }

    @Test
    @DisplayName("多个 !/ 时只剥到第一个")
    void should_only_strip_first_separator() {
        // 真实 jar 嵌套场景: jar://outer.jar!/inner.jar!/SKILL.md, 我们只取最外层 outer.jar
        assertThat(JarPathUtil.stripJarSuffix("/a/outer.jar!/inner.jar!/SKILL.md"))
            .isEqualTo("/a/outer.jar");
    }
}
