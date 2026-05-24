package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Maven/Gradle 风格的依赖坐标.
 *
 * <p>该模型只承载 {@code groupId}/{@code artifactId}/{@code version} 三段信息, 不强制要求都不为空,
 * 因为部分来源 (如本地 Jar) 无法解析出完整坐标. 调用方应通过 {@link #isComplete()} 判断信息完备性.</p>
 *
 * <p>选择独立类型而非简单 String 的原因:
 * <ul>
 *   <li>避免不同模块各自实现 "Maven: g:a:v" 字符串拆分, 统一拆分规则.</li>
 *   <li>方便后续接入 Marketplace / Publish 模块时复用相同坐标语义.</li>
 *   <li>不可变对象, 线程安全, 适合作为 SkillRegistry 缓存的一部分.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillCoordinate {

    /** IDEA Maven 库名前缀. */
    private static final String MAVEN_PREFIX = "Maven: ";

    /** IDEA Gradle 库名前缀. */
    private static final String GRADLE_PREFIX = "Gradle: ";

    /** Maven groupId, 可为 null. */
    @Nullable
    private final String groupId;

    /** Maven artifactId, 可为 null. */
    @Nullable
    private final String artifactId;

    /** 版本号, 可为 null. */
    @Nullable
    private final String version;

    /**
     * 构造一个坐标.
     *
     * @param groupId    Maven groupId
     * @param artifactId Maven artifactId
     * @param version    版本号
     */
    public SkillCoordinate(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    /**
     * 解析 IDEA 库名为坐标.
     *
     * <p>IDEA 中通过 Maven/Gradle 导入的依赖, 其 Library name 通常是
     * {@code "Maven: groupId:artifactId:version"} 或 {@code "Gradle: groupId:artifactId:version"} 形式.
     * 该方法负责剥离前缀并按 {@code :} 拆分, 容错处理类型 (test/sources/javadoc) 等额外段.</p>
     *
     * @param libraryName 库名, 可为 null
     * @return 坐标实例, 永不为 null. 解析不到任何字段时返回 {@link #unknown()}
     */
    @NotNull
    public static SkillCoordinate fromLibraryName(@Nullable String libraryName) {
        if (libraryName == null || libraryName.isBlank()) {
            return unknown();
        }

        String trimmed = libraryName.trim();
        if (trimmed.startsWith(MAVEN_PREFIX)) {
            trimmed = trimmed.substring(MAVEN_PREFIX.length());
        } else if (trimmed.startsWith(GRADLE_PREFIX)) {
            trimmed = trimmed.substring(GRADLE_PREFIX.length());
        }

        // groupId:artifactId:version 至少 3 段; 多余段 (如 type=jar / classifier) 忽略
        String[] parts = trimmed.split(":");
        if (parts.length < 3) {
            return new SkillCoordinate(null, null, null);
        }
        return new SkillCoordinate(parts[0], parts[1], parts[2]);
    }

    /**
     * 直接构造一个坐标.
     *
     * @param groupId    Maven groupId
     * @param artifactId Maven artifactId
     * @param version    版本号
     * @return 新的坐标实例
     */
    @NotNull
    public static SkillCoordinate of(@Nullable String groupId, @Nullable String artifactId, @Nullable String version) {
        return new SkillCoordinate(groupId, artifactId, version);
    }

    /**
     * 把 {@code findByCoordinate} 入参解析成查询坐标 (作为 {@link #matches(SkillCoordinate)} 的左侧).
     *
     * <p>规则:</p>
     * <ul>
     *   <li>{@code "g:a"} → 通配版本, 仅按 (groupId, artifactId) 匹配.</li>
     *   <li>{@code "g:a:v"} (或更多段, 多余段忽略) → 精确匹配.</li>
     *   <li>其他形式 (空 / 只有一段) → {@link #unknown()}, 与任何完整坐标都不匹配.</li>
     * </ul>
     *
     * <p>该方法属于公共 API: 给第三方插件查询坐标时复用同一份解析规则, 避免各自实现产生不一致.</p>
     *
     * @param query 查询字符串
     * @return 查询坐标
     */
    @NotNull
    public static SkillCoordinate parseQuery(@NotNull String query) {
        String[] parts = query.split(":");
        if (parts.length == 2) {
            return new SkillCoordinate(parts[0], parts[1], null);
        }
        if (parts.length >= 3) {
            return new SkillCoordinate(parts[0], parts[1], parts[2]);
        }
        return unknown();
    }

    /**
     * 判断本坐标 (查询侧) 是否匹配另一个目标坐标.
     *
     * <p>"查询侧" 即调用者从 {@link #parseQuery(String)} 拿到的坐标: 任一字段为 null
     * 视为通配该段. 对称语义: {@code of("g","a",null).matches(of("g","a","1"))} 为 true,
     * 但反过来不为 true. </p>
     *
     * @param target 目标坐标
     * @return 匹配返回 true
     */
    public boolean matches(@NotNull SkillCoordinate target) {
        if (this.groupId != null && !this.groupId.equals(target.groupId)) {
            return false;
        }
        if (this.artifactId != null && !this.artifactId.equals(target.artifactId)) {
            return false;
        }
        if (this.version != null && !this.version.equals(target.version)) {
            return false;
        }
        return true;
    }

    /**
     * 返回一个所有字段都为空的占位坐标, 用于无法识别坐标的来源 (例如本地 Jar).
     *
     * @return 占位坐标
     */
    @NotNull
    public static SkillCoordinate unknown() {
        return new SkillCoordinate(null, null, null);
    }

    @Nullable
    public String getGroupId() {
        return this.groupId;
    }

    @Nullable
    public String getArtifactId() {
        return this.artifactId;
    }

    @Nullable
    public String getVersion() {
        return this.version;
    }

    /**
     * 判断三段坐标是否都不为空.
     *
     * @return 都不为空时返回 true
     */
    public boolean isComplete() {
        return this.groupId != null && this.artifactId != null && this.version != null;
    }

    /**
     * 渲染成 {@code groupId:artifactId:version} 形式, 缺失字段以 {@code ?} 占位.
     *
     * @return 字符串表示
     */
    @NotNull
    public String toCoordinateString() {
        return safe(this.groupId) + ":" + safe(this.artifactId) + ":" + safe(this.version);
    }

    @NotNull
    private static String safe(@Nullable String value) {
        return value == null ? "?" : value;
    }

    @Override
    public String toString() {
        return this.toCoordinateString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillCoordinate that)) {
            return false;
        }
        return Objects.equals(this.groupId, that.groupId)
            && Objects.equals(this.artifactId, that.artifactId)
            && Objects.equals(this.version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version);
    }
}
