package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dev.dong4j.idea.skillsjars.helper.api.model.ExportPlan;
import dev.dong4j.idea.skillsjars.helper.api.model.ExportResult;
import dev.dong4j.idea.skillsjars.helper.api.model.InstallationStatus;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

/**
 * 真正落盘的导出执行器.
 *
 * <p>原子性策略 (尽量逼近, 但 OS 文件系统并非完全原子):</p>
 * <ol>
 *   <li>把 skill 内容写到 {@code <target>.tmp-<random>} 临时目录.</li>
 *   <li>把 manifest 写进临时目录.</li>
 *   <li>如果目标目录已存在, 先递归删除.</li>
 *   <li>把临时目录 atomic move 到目标目录 (失败时退化为非 atomic move).</li>
 * </ol>
 *
 * <p>步骤 3 与 4 之间存在毫秒级的"目标目录不存在"窗口期, 期间外部读取者会看到目录消失;
 * 但本插件的所有读取走 manifest, 这种窗口可接受. 真出现 IO 异常时整个 tmp 目录会被
 * 清理, 不会留下半成品.</p>
 *
 * <p>{@link InstallationStatus#UP_TO_DATE} 不会进入此执行器 (调用方应在 plan 阶段
 * 短路返回); 真要进来也只会刷一遍 manifest 时间戳, 不会真复制文件.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class ExportExecutor {

    private static final Logger LOG = Logger.getInstance(ExportExecutor.class);

    private static final SecureRandom RAND = new SecureRandom();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 执行导出.
     *
     * @param plan 来自 {@link ExportPlanner} 的计划 (可能已经被 UI 通过
     *             {@link ExportPlan#withTargetDirectoryName(String)} 改名)
     * @return 执行结果
     */
    @NotNull
    public ExportResult execute(@NotNull ExportPlan plan) {
        SkillJarArtifact artifact = plan.getArtifact();
        SkillDescriptor skill = plan.getSkill();
        Path target = plan.getTargetSkillRoot();
        Path agentParent = plan.getTargetDirectory().getPath();

        Path tempDir = null;
        try {
            // 1. 准备父目录
            Files.createDirectories(agentParent);

            // 2. 临时目录 (与目标在同一文件系统 / 同一 agent 父目录下, 保证 atomic move 能成)
            String tempName = target.getFileName().toString() + ".tmp-" + Long.toHexString(RAND.nextLong());
            tempDir = agentParent.resolve(tempName);
            Files.createDirectories(tempDir);

            // 3. 复制 skill 内容到临时目录, 同时收集每文件 sha256 用于 manifest
            String jarPath = artifact.getJarFile().getPath();
            int sep = jarPath.indexOf("!/");
            if (sep >= 0) {
                jarPath = jarPath.substring(0, sep);
            }
            List<ManifestSchema.FileEntry> manifestFiles;
            try (JarFile jar = new JarFile(Path.of(jarPath).toFile())) {
                manifestFiles = copyFilesAndHash(jar, skill, tempDir);
            }

            // 4. 写 manifest
            ManifestSchema manifest = buildManifest(plan, manifestFiles);
            Path manifestFile = tempDir.resolve(ManifestSchema.FILE_NAME);
            Files.writeString(manifestFile, ManifestJson.toJson(manifest), StandardCharsets.UTF_8);

            // 5. 替换目标目录
            if (Files.exists(target)) {
                deleteRecursively(target);
            }
            try {
                Files.move(tempDir, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                // 部分文件系统不支持 atomic move (跨设备 / Windows 等), 退化为普通 move
                LOG.debug("Atomic move failed, falling back to regular move: " + atomicFailed.getMessage());
                Files.move(tempDir, target);
            }
            tempDir = null;

            int written = manifestFiles.size() + 1; // +1 for manifest itself
            return ExportResult.success(plan.getStatus(), target, written);
        } catch (IOException e) {
            LOG.warn("Export failed for " + skill.getName(), e);
            return ExportResult.failure(plan.getStatus(), e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            // 异常时清理临时目录
            if (tempDir != null) {
                try {
                    deleteRecursively(tempDir);
                } catch (IOException cleanup) {
                    LOG.debug("Failed to clean tmp dir " + tempDir, cleanup);
                }
            }
        }
    }

    /**
     * 把 jar 内 skill 根目录下的所有文件复制到 tempDir, 同时计算每个文件的 sha256.
     */
    @NotNull
    private static List<ManifestSchema.FileEntry> copyFilesAndHash(@NotNull JarFile jar,
                                                                   @NotNull SkillDescriptor skill,
                                                                   @NotNull Path tempDir) throws IOException {
        List<ManifestSchema.FileEntry> entries = new ArrayList<>();
        // 按 path 排序保证 manifest 输出稳定
        List<SkillFileEntry> files = new ArrayList<>(skill.getFiles());
        files.sort(Comparator.comparing(SkillFileEntry::getRelativePath));

        for (SkillFileEntry f : files) {
            String entryName = skill.getJarEntryRoot() + f.getRelativePath();
            JarEntry je = jar.getJarEntry(entryName);
            if (je == null) {
                throw new IOException("Missing jar entry: " + entryName);
            }
            Path target = tempDir.resolve(f.getRelativePath());
            Files.createDirectories(target.getParent());
            // 一边复制一边算 sha 避免读两次
            byte[] bytes;
            try (InputStream in = jar.getInputStream(je)) {
                bytes = in.readAllBytes();
            }
            Files.write(target, bytes);
            String sha = HashUtil.sha256(bytes);
            entries.add(new ManifestSchema.FileEntry(f.getRelativePath(), sha, bytes.length));
        }
        return entries;
    }

    /**
     * 构造写入磁盘的 manifest.
     */
    @NotNull
    private static ManifestSchema buildManifest(@NotNull ExportPlan plan,
                                                @NotNull List<ManifestSchema.FileEntry> files) {
        SkillJarArtifact artifact = plan.getArtifact();
        SkillDescriptor skill = plan.getSkill();

        // 来源 jar 的内容指纹: 用 ExportPlanner 的同一聚合算法保证 OUTDATED 判定一致
        String sourceJarSha = ExportPlanner.computeSkillSha(artifact, skill);
        if (sourceJarSha == null) {
            sourceJarSha = "";
        }

        return new ManifestSchema(
            ManifestSchema.SCHEMA_VERSION,
            ManifestSchema.MANAGED_BY,
            artifact.getCoordinate().toCoordinateString(),
            artifact.getSourceType(),
            skill.getName(),
            sourceJarSha,
            skill.getJarEntryRoot(),
            ISO.format(OffsetDateTime.now(ZoneId.systemDefault())),
            plan.getTargetDirectory().getAgentId(),
            plan.getTargetSkillRoot().toString(),
            files
        );
    }

    /**
     * 递归删除目录或文件.
     */
    private static void deleteRecursively(@NotNull Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (RuntimeException re) {
            if (re.getCause() instanceof IOException io) {
                throw io;
            }
            throw re;
        }
    }
}
