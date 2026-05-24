package dev.dong4j.idea.skillsjars.helper.api.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * jar 内 skill 根目录下的一个文件入口.
 *
 * <p>用于在解析阶段就把每个 skill 的"将来要复制哪些文件"枚举出来, 导出阶段直接消费.
 * 不在解析阶段计算 sha256, 因为 hash 计算在 ExportPlanner / ExportExecutor 阶段
 * 才需要; 解析期保持轻量.</p>
 *
 * <p>{@code relativePath} 总是相对 skill 根目录, 不带前导 {@code /}, 例如
 * {@code SKILL.md}, {@code examples/review.md}, {@code scripts/check.sh}.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillFileEntry {

    @NotNull
    private final String relativePath;

    private final long size;

    public SkillFileEntry(@NotNull String relativePath, long size) {
        this.relativePath = relativePath;
        this.size = size;
    }

    @NotNull
    public String getRelativePath() {
        return this.relativePath;
    }

    public long getSize() {
        return this.size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SkillFileEntry that)) {
            return false;
        }
        return this.size == that.size && Objects.equals(this.relativePath, that.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.relativePath, this.size);
    }

    @Override
    public String toString() {
        return "SkillFileEntry{path=" + this.relativePath + ", size=" + this.size + '}';
    }
}
