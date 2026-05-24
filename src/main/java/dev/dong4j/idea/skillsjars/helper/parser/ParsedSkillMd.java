package dev.dong4j.idea.skillsjars.helper.parser;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * SKILL.md 解析中间结果.
 *
 * <p>{@link SkillFrontmatterParser} 把 frontmatter 与正文拆开后输出该结构, 然后协调层把它包装成
 * {@code SkillDescriptor}. 单独抽出来是因为:</p>
 * <ul>
 *   <li>测试 frontmatter 解析时不需要构造完整 jar 路径.</li>
 *   <li>未来可能需要从文件系统而不是 jar 中解析同样的 SKILL.md (例如 Local Publish 校验).</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ParsedSkillMd {

    @Nullable
    private final String name;

    @Nullable
    private final String description;

    @NotNull
    private final List<String> allowedTools;

    @Nullable
    private final String license;

    @NotNull
    private final String body;

    /**
     * 构造解析结果.
     *
     * @param name         frontmatter 中的 name
     * @param description  frontmatter 中的 description
     * @param allowedTools frontmatter 中的 allowed-tools 拆分结果
     * @param license      frontmatter 中的 license
     * @param body         去掉 frontmatter 之后的正文
     */
    public ParsedSkillMd(@Nullable String name,
                         @Nullable String description,
                         @NotNull List<String> allowedTools,
                         @Nullable String license,
                         @NotNull String body) {
        this.name = name;
        this.description = description;
        this.allowedTools = List.copyOf(allowedTools);
        this.license = license;
        this.body = body;
    }

    @Nullable
    public String getName() {
        return this.name;
    }

    @Nullable
    public String getDescription() {
        return this.description;
    }

    @NotNull
    public List<String> getAllowedTools() {
        return Collections.unmodifiableList(this.allowedTools);
    }

    @Nullable
    public String getLicense() {
        return this.license;
    }

    @NotNull
    public String getBody() {
        return this.body;
    }
}
