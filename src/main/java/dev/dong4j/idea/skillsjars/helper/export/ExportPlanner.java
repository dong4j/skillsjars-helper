package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
                String currentJarSha = SkillContentHasher.hash(artifact, skill);
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

}
