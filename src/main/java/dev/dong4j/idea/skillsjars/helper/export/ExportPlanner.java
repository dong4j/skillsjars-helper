package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.InstallationStatus;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillTargetDirectory;

/**
 * 导出执行前的状态分析器.
 *
 * <p>核心职责: 比对 "当前 jar 里这个 skill 的内容" 与 "目标目录里已经存在的内容
 * (含其 manifest)", 判定 6 种 {@link InstallationStatus} 之一, 产出
 * {@link ExportPlan}.</p>
 *
 * <p>判定算法 (按优先级):</p>
 * <ol>
 *   <li>目标子目录不存在 → {@link InstallationStatus#NEW}</li>
 *   <li>目标子目录存在但没有 / 损坏的 manifest → {@link InstallationStatus#FOREIGN}</li>
 *   <li>manifest 来源 (artifact + skillRoot) 与当前 skill 不一致 →
 *       {@link InstallationStatus#DUPLICATE_NAME}</li>
 *   <li>manifest 来源一致, 任意磁盘文件的 sha 与 manifest 不符 →
 *       {@link InstallationStatus#LOCALLY_MODIFIED}</li>
 *   <li>manifest 来源一致, jar sha 与 manifest 不一致 →
 *       {@link InstallationStatus#OUTDATED}</li>
 *   <li>以上都对得上 → {@link InstallationStatus#UP_TO_DATE}</li>
 * </ol>
 *
 * <p>所有 IO 都是只读的, 不会触碰目标目录之外的状态.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExportPlanner {

    private static final Logger LOG = Logger.getInstance(ExportPlanner.class);

    /**
     * 计算导出计划.
     *
     * <p>{@code targetDirectory} 是 Agent 的父目录 (如 .claude/skills), 不是 skill
     * 落地子目录; 子目录名由 {@link ExportNaming#defaultDirectoryName} 决定.</p>
     */
    @NotNull
    public ExportPlan plan(@NotNull SkillJarArtifact artifact,
                           @NotNull SkillDescriptor skill,
                           @NotNull SkillTargetDirectory targetDirectory) {
        String dirName = ExportNaming.defaultDirectoryName(skill);
        Path targetSkillRoot = targetDirectory.getPath().resolve(dirName);

        InstallationStatus status;
        String conflictCoord = null;

        if (!Files.isDirectory(targetSkillRoot)) {
            status = InstallationStatus.NEW;
        } else {
            ManifestSchema existing = readManifest(targetSkillRoot);
            if (existing == null || !existing.isManagedByThisPlugin()) {
                status = InstallationStatus.FOREIGN;
            } else if (!existing.matchesSource(artifact.getCoordinate().toCoordinateString(),
                                               skill.getJarEntryRoot())) {
                status = InstallationStatus.DUPLICATE_NAME;
                conflictCoord = existing.getArtifact();
            } else {
                String currentJarSha = computeSkillSha(artifact, skill);
                if (currentJarSha == null) {
                    // jar 暂时不可读, 视为 OUTDATED 让用户主动覆盖
                    status = InstallationStatus.OUTDATED;
                } else if (!existing.getSourceJarSha256().equals(currentJarSha)) {
                    status = isLocallyModified(targetSkillRoot, existing)
                        ? InstallationStatus.LOCALLY_MODIFIED
                        : InstallationStatus.OUTDATED;
                } else if (isLocallyModified(targetSkillRoot, existing)) {
                    status = InstallationStatus.LOCALLY_MODIFIED;
                } else {
                    status = InstallationStatus.UP_TO_DATE;
                }
            }
        }

        return new ExportPlan(
            status,
            artifact,
            skill,
            targetDirectory,
            dirName,
            targetSkillRoot,
            skill.getFiles(),
            conflictCoord
        );
    }

    /**
     * 读取目标目录里的 manifest. 文件不存在 / 解析失败时返回 null.
     */
    @Nullable
    private static ManifestSchema readManifest(@NotNull Path skillRoot) {
        Path manifestPath = skillRoot.resolve(ManifestSchema.FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }
        try {
            String content = Files.readString(manifestPath);
            return ManifestJson.fromJson(content);
        } catch (IOException e) {
            LOG.debug("Failed to read manifest at " + manifestPath, e);
            return null;
        }
    }

    /**
     * 检查目标目录里是否有任何文件的 sha 与 manifest 记录不符.
     *
     * <p>多扫描的文件 (manifest 里没记的) 不视为 LOCALLY_MODIFIED, 因为用户也可能在该
     * 目录里随手放了别的文件 (例如笔记). 我们只关心 manifest 自己声称管理的那批文件
     * 是否被改过. </p>
     */
    private static boolean isLocallyModified(@NotNull Path skillRoot, @NotNull ManifestSchema manifest) {
        for (ManifestSchema.FileEntry expected : manifest.getFiles()) {
            Path file = skillRoot.resolve(expected.getPath());
            if (!Files.isRegularFile(file)) {
                return true;
            }
            try {
                String actualSha = HashUtil.sha256(file);
                if (!actualSha.equals(expected.getSha256())) {
                    return true;
                }
            } catch (RuntimeException e) {
                LOG.debug("Failed to hash " + file, e);
                return true;
            }
        }
        return false;
    }

    /**
     * 计算 "当前 skill 的 jar 内内容指纹".
     *
     * <p>不用整个 jar 的 sha (同 jar 内其他 skill 的变化不应触发本 skill 的 OUTDATED);
     * 也不用单一 SKILL.md 的 sha (同 skill 内的辅助文件 examples / scripts 变化也应触发).
     * 实现: 把该 skill 根目录下所有文件的 (path + sha) 拼成一个聚合字符串再求一次 sha. </p>
     */
    @Nullable
    static String computeSkillSha(@NotNull SkillJarArtifact artifact, @NotNull SkillDescriptor skill) {
        String jarPath = artifact.getJarFile().getPath();
        int sep = jarPath.indexOf("!/");
        if (sep >= 0) {
            jarPath = jarPath.substring(0, sep);
        }
        Path path = Path.of(jarPath);
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            StringBuilder digestSource = new StringBuilder();
            // skill.files 已经按 jar entry 顺序记录, 但为了保证哈希稳定性按 path 排序
            java.util.List<dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry> sorted =
                new java.util.ArrayList<>(skill.getFiles());
            sorted.sort(java.util.Comparator.comparing(
                dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry::getRelativePath));
            for (var f : sorted) {
                String entryName = skill.getJarEntryRoot() + f.getRelativePath();
                JarEntry entry = jar.getJarEntry(entryName);
                if (entry == null) {
                    return null;
                }
                String fileSha;
                try (InputStream in = jar.getInputStream(entry)) {
                    fileSha = HashUtil.sha256Stream(in);
                }
                digestSource.append(f.getRelativePath()).append(':').append(fileSha).append('\n');
            }
            return HashUtil.sha256(digestSource.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.debug("Failed to hash jar " + path, e);
            return null;
        }
    }
}
