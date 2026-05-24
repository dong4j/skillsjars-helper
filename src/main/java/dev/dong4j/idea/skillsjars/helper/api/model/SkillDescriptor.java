package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 单个 Skill 的元数据描述.
 *
 * <p>对应 Jar 内一份 {@code SKILL.md} 文件以及它所在的资源根目录. 该模型只描述 "这是什么 Skill",
 * 不涉及导出状态、manifest、风险评分等上层概念. 这样让上层模块 (导出 / 检查 / 风险) 都基于同一
 * 个稳定的描述符做扩展, 避免 API 频繁变动.</p>
 *
 * <p>字段说明:</p>
 * <ul>
 *   <li>{@link #name}: 优先取 frontmatter 的 {@code name}, 否则退化为 Skill 根目录名.</li>
 *   <li>{@link #description}: frontmatter 中的 {@code description}, 可为 null.</li>
 *   <li>{@link #allowedTools}: frontmatter 中 {@code allowed-tools} 拆分得到的列表; 没有时为空列表.</li>
 *   <li>{@link #license}: frontmatter 中的 {@code license}, 可为 null.</li>
 *   <li>{@link #jarEntryRoot}: Skill 在 Jar 内的根路径 (以 {@code /} 结尾), 例如 {@code META-INF/skills/dev/dong4j/code-review/}.</li>
 *   <li>{@link #skillMdPath}: {@code SKILL.md} 在 Jar 内的完整路径.</li>
 *   <li>{@link #body}: SKILL.md 去掉 frontmatter 之后的正文, 用于预览展示.</li>
 *   <li>{@link #files}: skill 根目录下所有文件清单 (含 SKILL.md 自身), 导出阶段直接消费, 避免再次打开 jar.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillDescriptor {

    /** Skill 名称 (优先 frontmatter, 退化为目录名), 不为 null. */
    @NotNull
    private final String name;

    /** Skill 描述, 可为 null. */
    @Nullable
    private final String description;

    /** {@code allowed-tools} 拆分结果, 不为 null. */
    @NotNull
    private final List<String> allowedTools;

    /** 许可证, 可为 null. */
    @Nullable
    private final String license;

    /** Skill 在 Jar 内的根目录, 以 / 结尾. */
    @NotNull
    private final String jarEntryRoot;

    /** SKILL.md 在 Jar 内的完整路径. */
    @NotNull
    private final String skillMdPath;

    /** SKILL.md 去掉 frontmatter 之后的正文. */
    @NotNull
    private final String body;

    /** Skill 根目录下所有文件清单 (含 SKILL.md 自身), 路径相对 jarEntryRoot. */
    @NotNull
    private final List<SkillFileEntry> files;

    /**
     * 构造 Skill 描述符 (files 默认空列表, 兼容旧调用).
     */
    public SkillDescriptor(@NotNull String name,
                           @Nullable String description,
                           @NotNull List<String> allowedTools,
                           @Nullable String license,
                           @NotNull String jarEntryRoot,
                           @NotNull String skillMdPath,
                           @NotNull String body) {
        this(name, description, allowedTools, license, jarEntryRoot, skillMdPath, body, List.of());
    }

    /**
     * 构造 Skill 描述符 (含文件清单).
     *
     * @param name         Skill 名称
     * @param description  描述
     * @param allowedTools allowed-tools 列表
     * @param license      许可证
     * @param jarEntryRoot Jar 内根目录
     * @param skillMdPath  SKILL.md 完整路径
     * @param body         正文
     * @param files        skill 根目录下所有文件清单
     */
    public SkillDescriptor(@NotNull String name,
                           @Nullable String description,
                           @NotNull List<String> allowedTools,
                           @Nullable String license,
                           @NotNull String jarEntryRoot,
                           @NotNull String skillMdPath,
                           @NotNull String body,
                           @NotNull List<SkillFileEntry> files) {
        this.name = name;
        this.description = description;
        this.allowedTools = List.copyOf(allowedTools);
        this.license = license;
        this.jarEntryRoot = jarEntryRoot;
        this.skillMdPath = skillMdPath;
        this.body = body;
        this.files = List.copyOf(files);
    }

    @NotNull
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
    public String getJarEntryRoot() {
        return this.jarEntryRoot;
    }

    @NotNull
    public String getSkillMdPath() {
        return this.skillMdPath;
    }

    @NotNull
    public String getBody() {
        return this.body;
    }

    /**
     * 获取 skill 根目录下所有文件清单 (含 SKILL.md 自身).
     */
    @NotNull
    public List<SkillFileEntry> getFiles() {
        return Collections.unmodifiableList(this.files);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillDescriptor that)) {
            return false;
        }
        return Objects.equals(this.name, that.name)
            && Objects.equals(this.skillMdPath, that.skillMdPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.skillMdPath);
    }

    @Override
    public String toString() {
        return "SkillDescriptor{name=" + this.name + ", path=" + this.skillMdPath + '}';
    }
}
