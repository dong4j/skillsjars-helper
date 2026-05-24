package dev.dong4j.idea.skillsjars.helper.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md frontmatter 解析器.
 *
 * <p>SkillsJars 中 SKILL.md 的 frontmatter 采用 YAML 风格的 {@code ---} 包裹块, 但一期只需要支持
 * Anthropic Agent Skills 文档约定的最小子集:</p>
 *
 * <pre>{@code
 * ---
 * name: code-review
 * description: 自动化代码审查
 * allowed-tools: Read, Grep, Glob
 * license: MIT
 * ---
 * # 正文 ...
 * }</pre>
 *
 * <p>实现选择:</p>
 * <ul>
 *   <li>不引入完整 YAML 解析库 (snakeyaml 等), 避免与 IDEA 平台自带版本冲突.</li>
 *   <li>只支持简单的 {@code key: value} 语法和数组的内联 (逗号或空白分隔) 写法,
 *       不支持嵌套对象/多行折叠. 这覆盖 SkillsJars 现有约定, 后续如果出现复杂场景再升级.</li>
 *   <li>frontmatter 缺失时直接把整段 markdown 当作正文返回, 不抛异常.</li>
 * </ul>
 *
 * <p>解析过程是纯函数式, 线程安全.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillFrontmatterParser {

    private static final String DELIMITER = "---";

    private SkillFrontmatterParser() {
    }

    /**
     * 解析整个 SKILL.md 文本.
     *
     * @param markdown 原始内容, 不应为 null
     * @return 解析结果, 永远非 null
     */
    @NotNull
    public static ParsedSkillMd parse(@NotNull String markdown) {
        // 移除 BOM, 统一换行
        String normalized = stripBom(markdown).replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);

        // frontmatter 必须以 --- 起始
        if (lines.length == 0 || !DELIMITER.equals(lines[0].trim())) {
            return new ParsedSkillMd(null, null, Collections.emptyList(), null, normalized.trim());
        }

        int closingIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            if (DELIMITER.equals(lines[i].trim())) {
                closingIndex = i;
                break;
            }
        }
        if (closingIndex < 0) {
            // 没有结束分隔符, 视为没有 frontmatter
            return new ParsedSkillMd(null, null, Collections.emptyList(), null, normalized.trim());
        }

        Map<String, String> kv = parseKeyValueBlock(Arrays.copyOfRange(lines, 1, closingIndex));
        String body = String.join("\n", Arrays.copyOfRange(lines, closingIndex + 1, lines.length)).trim();

        return new ParsedSkillMd(
            normalize(kv.get("name")),
            normalize(kv.get("description")),
            splitAllowedTools(kv.get("allowed-tools")),
            normalize(kv.get("license")),
            body
        );
    }

    /**
     * 把简单 {@code key: value} 块解析为 Map.
     *
     * <p>规则:</p>
     * <ul>
     *   <li>忽略空行和以 {@code #} 开头的注释行.</li>
     *   <li>键统一转小写, 便于查询.</li>
     *   <li>剥离值两侧的双引号或单引号.</li>
     *   <li>遇到没有冒号的行直接跳过, 不抛异常 (容错).</li>
     * </ul>
     */
    @NotNull
    private static Map<String, String> parseKeyValueBlock(@NotNull String[] lines) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = line.substring(colon + 1).trim();
            value = stripQuotes(value);
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * 将 allowed-tools 拆分成列表.
     *
     * <p>支持以下写法:</p>
     * <ul>
     *   <li>{@code Read, Grep, Glob}</li>
     *   <li>{@code Read Grep Glob}</li>
     *   <li>{@code [Read, Grep, Glob]}</li>
     *   <li>空字符串或 null 返回空列表</li>
     * </ul>
     */
    @NotNull
    private static List<String> splitAllowedTools(@Nullable String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        // 同时按 , 和空白拆, 避免两种写法都得二次清洗
        String[] tokens = trimmed.split("[,\\s]+");
        List<String> tools = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String t = stripQuotes(token.trim());
            if (!t.isEmpty()) {
                tools.add(t);
            }
        }
        return tools;
    }

    @Nullable
    private static String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NotNull
    private static String stripQuotes(@NotNull String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @NotNull
    private static String stripBom(@NotNull String input) {
        if (!input.isEmpty() && input.charAt(0) == '\uFEFF') {
            return input.substring(1);
        }
        return input;
    }
}
