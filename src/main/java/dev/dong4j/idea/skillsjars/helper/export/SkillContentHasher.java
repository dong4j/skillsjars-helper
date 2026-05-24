package dev.dong4j.idea.skillsjars.helper.export;

import com.intellij.openapi.diagnostic.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillFileEntry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.parser.JarPathUtil;

/**
 * Skill 内容指纹计算器.
 *
 * <p>导出流程里, "当前 jar 里这个 skill 的内容指纹" 同时被以下两端消费:</p>
 * <ul>
 *   <li>{@link ExportPlanner}: 判定 {@code OUTDATED} 与否, 需要把 manifest 里的
 *       {@code sourceJarSha256} 与"jar 当前的 skill 指纹"比对.</li>
 *   <li>{@link ExportExecutor}: 写 manifest 时记录 {@code sourceJarSha256}, 必须与
 *       Planner 使用同一算法, 否则导出后立刻又被判定为 OUTDATED.</li>
 * </ul>
 *
 * <p>历史上该算法直接定义在 {@code ExportPlanner.computeSkillSha} 上, 由 Executor 反向
 * 静态调用 Planner, 造成 export 子包内的环形依赖 (执行层指向决策层). 抽到独立工具后,
 * 两端都只依赖本类, 职责更清晰. </p>
 *
 * <h2>算法</h2>
 *
 * <ol>
 *   <li>不用整个 jar 的 sha (同 jar 内其他 skill 的变化不应触发本 skill 的 OUTDATED).</li>
 *   <li>不用单个 SKILL.md 的 sha (同 skill 内 examples / scripts 变化也应触发).</li>
 *   <li>实现: 把该 skill 根目录下所有文件的 {@code (path + sha)} 按 path 排序后拼成聚合字符串,
 *       再求一次 sha256. 输入文件顺序无关, 只依赖文件内容.</li>
 * </ol>
 *
 * @author dong4j
 * @since 1.0.0
 */
public final class SkillContentHasher {

    private static final Logger LOG = Logger.getInstance(SkillContentHasher.class);

    private SkillContentHasher() {
    }

    /**
     * 计算 {@code skill} 在 {@code artifact} 当前 jar 内容下的聚合指纹.
     *
     * <p>jar 不可读 / 某个声明的 entry 不存在时返回 null, 调用方按"无法判定"语义处理
     * (Planner 会退化为 OUTDATED, Executor 会写入空串).</p>
     *
     * @param artifact 来源 jar 制品
     * @param skill    待计算的 skill
     * @return 64 字符的小写 hex 字符串; 计算失败返回 null
     */
    @Nullable
    public static String hash(@NotNull SkillJarArtifact artifact, @NotNull SkillDescriptor skill) {
        Path path = JarPathUtil.toLocalJarPath(artifact.getJarFile().getPath());
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            // 按 relativePath 排序保证哈希稳定, 与 jar entry 物理顺序解耦
            List<SkillFileEntry> sorted = new ArrayList<>(skill.getFiles());
            sorted.sort(Comparator.comparing(SkillFileEntry::getRelativePath));

            StringBuilder digestSource = new StringBuilder();
            for (SkillFileEntry f : sorted) {
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
            return HashUtil.sha256(digestSource.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOG.debug("Failed to hash jar " + path, e);
            return null;
        }
    }
}
