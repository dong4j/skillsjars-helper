package dev.dong4j.idea.skillsjars.helper.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SkillFrontmatterParser} 单元测试.
 *
 * <p>覆盖以下场景:</p>
 * <ul>
 *   <li>典型的 frontmatter 解析.</li>
 *   <li>无 frontmatter 时回退到全文为 body.</li>
 *   <li>frontmatter 缺少结束分隔符的容错.</li>
 *   <li>{@code allowed-tools} 多种写法 (逗号 / 空格 / 中括号).</li>
 *   <li>BOM 与 CRLF 兼容.</li>
 * </ul>
 *
 * @author dong4j
 */
class SkillFrontmatterParserTest {

    @Test
    @DisplayName("典型 frontmatter 应被正确解析")
    void should_parse_typical_frontmatter() {
        String md = """
            ---
            name: code-review
            description: 自动化代码审查
            allowed-tools: Read, Grep, Glob
            license: MIT
            ---
            # 正文
            内容
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);

        assertThat(parsed.getName()).isEqualTo("code-review");
        assertThat(parsed.getDescription()).isEqualTo("自动化代码审查");
        assertThat(parsed.getAllowedTools()).containsExactly("Read", "Grep", "Glob");
        assertThat(parsed.getLicense()).isEqualTo("MIT");
        assertThat(parsed.getBody()).contains("# 正文").contains("内容");
    }

    @Test
    @DisplayName("没有 frontmatter 时整段当作 body")
    void should_fallback_when_no_frontmatter() {
        String md = "# 标题\n正文";
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);

        assertThat(parsed.getName()).isNull();
        assertThat(parsed.getDescription()).isNull();
        assertThat(parsed.getAllowedTools()).isEmpty();
        assertThat(parsed.getLicense()).isNull();
        assertThat(parsed.getBody()).isEqualTo("# 标题\n正文");
    }

    @Test
    @DisplayName("缺少结束分隔符时回退到无 frontmatter")
    void should_handle_missing_closing_delimiter() {
        String md = """
            ---
            name: code-review
            正文
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);

        assertThat(parsed.getName()).isNull();
        assertThat(parsed.getBody()).contains("name: code-review").contains("正文");
    }

    @Test
    @DisplayName("allowed-tools 支持中括号语法")
    void should_parse_bracket_allowed_tools() {
        String md = """
            ---
            name: x
            allowed-tools: [Read, Bash]
            ---
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);
        assertThat(parsed.getAllowedTools()).containsExactly("Read", "Bash");
    }

    @Test
    @DisplayName("allowed-tools 支持空白分隔")
    void should_parse_whitespace_allowed_tools() {
        String md = """
            ---
            name: x
            allowed-tools: Read Grep
            ---
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);
        assertThat(parsed.getAllowedTools()).containsExactly("Read", "Grep");
    }

    @Test
    @DisplayName("BOM 和 CRLF 都应正确处理")
    void should_normalize_bom_and_crlf() {
        String md = "\uFEFF---\r\nname: x\r\n---\r\nbody";
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);
        assertThat(parsed.getName()).isEqualTo("x");
        assertThat(parsed.getBody()).isEqualTo("body");
    }

    @Test
    @DisplayName("引号包裹的值应被剥除")
    void should_strip_quotes() {
        String md = """
            ---
            name: "code-review"
            description: '自动化'
            ---
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);
        assertThat(parsed.getName()).isEqualTo("code-review");
        assertThat(parsed.getDescription()).isEqualTo("自动化");
    }

    @Test
    @DisplayName("空 allowed-tools 返回空列表")
    void should_handle_empty_allowed_tools() {
        String md = """
            ---
            name: x
            allowed-tools:
            ---
            """;
        ParsedSkillMd parsed = SkillFrontmatterParser.parse(md);
        assertThat(parsed.getAllowedTools()).isEmpty();
    }
}
