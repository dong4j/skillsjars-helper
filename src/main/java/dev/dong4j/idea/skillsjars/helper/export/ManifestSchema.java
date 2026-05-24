package dev.dong4j.idea.skillsjars.helper.export;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * 导出后写入到 skill 子目录里的 {@code .skillsjars-helper.json}.
 *
 * <p>用途:</p>
 * <ul>
 *   <li>判断目标目录是否由本插件管理 (与 {@link #MANAGED_BY} 比对).</li>
 *   <li>判断来源 jar 是否更新 ({@link #sourceJarSha256} 比对).</li>
 *   <li>判断目标目录是否被手工修改 ({@link #files} 中每文件 sha256 比对).</li>
 *   <li>判断同名冲突 (来源 artifact + jarEntryRoot 与当前 skill 是否一致).</li>
 * </ul>
 *
 * <p>字段顺序在写入时固定保持下面声明顺序, 便于 diff 和阅读.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ManifestSchema {

    /** 固定值, 用于识别 manifest 来源. */
    public static final String MANAGED_BY = "skillsjars-helper";
    /** 固定文件名. */
    public static final String FILE_NAME = ".skillsjars-helper.json";
    /** 当前 schema 版本, 字段不向前兼容时递增. */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    @NotNull private final String managedBy;
    @NotNull private final String artifact;
    @NotNull private final SkillSourceType sourceType;
    @NotNull private final String skill;
    @NotNull private final String sourceJarSha256;
    @NotNull private final String skillRoot;
    @NotNull private final String installedAt;
    @NotNull private final String targetAgent;
    @NotNull private final String targetPath;
    @NotNull private final List<FileEntry> files;

    public ManifestSchema(int schemaVersion,
                          @NotNull String managedBy,
                          @NotNull String artifact,
                          @NotNull SkillSourceType sourceType,
                          @NotNull String skill,
                          @NotNull String sourceJarSha256,
                          @NotNull String skillRoot,
                          @NotNull String installedAt,
                          @NotNull String targetAgent,
                          @NotNull String targetPath,
                          @NotNull List<FileEntry> files) {
        this.schemaVersion = schemaVersion;
        this.managedBy = managedBy;
        this.artifact = artifact;
        this.sourceType = sourceType;
        this.skill = skill;
        this.sourceJarSha256 = sourceJarSha256;
        this.skillRoot = skillRoot;
        this.installedAt = installedAt;
        this.targetAgent = targetAgent;
        this.targetPath = targetPath;
        this.files = List.copyOf(files);
    }

    public int getSchemaVersion() { return this.schemaVersion; }
    @NotNull public String getManagedBy() { return this.managedBy; }
    @NotNull public String getArtifact() { return this.artifact; }
    @NotNull public SkillSourceType getSourceType() { return this.sourceType; }
    @NotNull public String getSkill() { return this.skill; }
    @NotNull public String getSourceJarSha256() { return this.sourceJarSha256; }
    @NotNull public String getSkillRoot() { return this.skillRoot; }
    @NotNull public String getInstalledAt() { return this.installedAt; }
    @NotNull public String getTargetAgent() { return this.targetAgent; }
    @NotNull public String getTargetPath() { return this.targetPath; }
    @NotNull public List<FileEntry> getFiles() { return Collections.unmodifiableList(this.files); }

    /**
     * 判断该 manifest 是不是由本插件写的, 否则视为 FOREIGN.
     */
    public boolean isManagedByThisPlugin() {
        return MANAGED_BY.equals(this.managedBy);
    }

    /**
     * 判断当前 manifest 描述的是不是和给定 (artifact, skillRoot) 同一个 skill.
     * 用于 DUPLICATE_NAME 判断.
     */
    public boolean matchesSource(@NotNull String artifact, @NotNull String skillRoot) {
        return this.artifact.equals(artifact) && this.skillRoot.equals(skillRoot);
    }

    /**
     * 单个文件的 sha256 + 大小, 写在 manifest 的 files 数组里.
     */
    public static final class FileEntry {

        @NotNull private final String path;
        @NotNull private final String sha256;
        private final long size;

        public FileEntry(@NotNull String path, @NotNull String sha256, long size) {
            this.path = path;
            this.sha256 = sha256;
            this.size = size;
        }

        @NotNull public String getPath() { return this.path; }
        @NotNull public String getSha256() { return this.sha256; }
        public long getSize() { return this.size; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FileEntry that)) return false;
            return this.size == that.size
                && this.path.equals(that.path)
                && this.sha256.equals(that.sha256);
        }

        @Override
        public int hashCode() {
            int r = this.path.hashCode();
            r = 31 * r + this.sha256.hashCode();
            r = 31 * r + Long.hashCode(this.size);
            return r;
        }
    }

    /**
     * 在 files 列表里按 path 找一项, 没有则 null.
     */
    @Nullable
    public FileEntry findFile(@NotNull String relativePath) {
        for (FileEntry entry : this.files) {
            if (entry.path.equals(relativePath)) {
                return entry;
            }
        }
        return null;
    }
}
