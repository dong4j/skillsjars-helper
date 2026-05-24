package dev.dong4j.idea.skillsjars.helper.parser;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * Jar 内 SKILL.md 解析器.
 *
 * <p>负责把扫描器产出的候选 Jar 打开, 找出所有命中
 * {@code META-INF/skills/**\/SKILL.md} 或
 * {@code META-INF/resources/skills/**\/SKILL.md} 的入口, 解析 frontmatter,
 * 最终输出一个 {@link SkillJarArtifact}.</p>
 *
 * <p>实现采用标准 {@link JarFile} 而不是 IDEA 的 {@code JarFileSystem}:</p>
 * <ul>
 *   <li>{@link JarFile} 在测试中无需启动 IDEA 容器, 便于编写单元测试.</li>
 *   <li>VirtualFile 仍然作为对外标识, 通过 {@code jarVirtualFile.getPath()} 拿到本地路径.</li>
 *   <li>没有命中入口的 Jar 会返回 null, 避免协调层堆积空艺人.</li>
 * </ul>
 *
 * <p>线程安全: 实现是无状态的纯函数, 可在任意线程调用.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SkillJarParser {

    private static final Logger LOG = Logger.getInstance(SkillJarParser.class);

    /** Jar 内 SkillsJars 标准路径前缀. */
    private static final String[] SKILL_ROOT_PREFIXES = {
        "META-INF/skills/",
        "META-INF/resources/skills/"
    };

    /** SKILL.md 文件名 (大小写敏感, 与官方约定一致). */
    private static final String SKILL_MD = "SKILL.md";

    /**
     * 解析一个 Jar 文件中的所有 Skill.
     *
     * @param jarVirtualFile Jar 文件的 IDEA 抽象, 用于回填到 {@link SkillJarArtifact}
     * @param sourceType     由扫描器标注的来源类型
     * @param coordinate     扫描器解析得到的坐标
     * @param libraryName    扫描器提供的库名 (用于调试)
     * @return Jar 制品; 没有任何 SKILL.md 时返回 null
     */
    @Nullable
    public SkillJarArtifact parse(@NotNull VirtualFile jarVirtualFile,
                                  @NotNull SkillSourceType sourceType,
                                  @NotNull SkillCoordinate coordinate,
                                  @Nullable String libraryName) {
        Path jarPath = resolveLocalPath(jarVirtualFile);
        if (jarPath == null) {
            LOG.debug("Skip non-local jar: " + jarVirtualFile.getPath());
            return null;
        }
        return this.parse(jarPath, jarVirtualFile, sourceType, coordinate, libraryName);
    }

    /**
     * 提供一个直接基于本地路径的入口, 主要给单元测试使用.
     *
     * @param jarPath        本地 Jar 路径
     * @param jarVirtualFile Jar 对应的 VirtualFile
     * @param sourceType     来源类型
     * @param coordinate     坐标
     * @param libraryName    库名
     * @return Jar 制品; 没有任何 SKILL.md 时返回 null
     */
    @Nullable
    public SkillJarArtifact parse(@NotNull Path jarPath,
                                  @NotNull VirtualFile jarVirtualFile,
                                  @NotNull SkillSourceType sourceType,
                                  @NotNull SkillCoordinate coordinate,
                                  @Nullable String libraryName) {
        if (!jarPath.toFile().isFile()) {
            return null;
        }

        List<SkillDescriptor> skills = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (!isSkillMdEntry(entry.getName())) {
                    continue;
                }
                SkillDescriptor descriptor = readDescriptor(jarFile, entry);
                if (descriptor != null) {
                    skills.add(descriptor);
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to read jar: " + jarPath, e);
            return null;
        }

        if (skills.isEmpty()) {
            return null;
        }
        return new SkillJarArtifact(jarVirtualFile, sourceType, coordinate, skills, libraryName);
    }

    /**
     * 判断条目是否符合 SkillsJars 约定路径.
     */
    private static boolean isSkillMdEntry(@NotNull String entryName) {
        if (!entryName.endsWith("/" + SKILL_MD)) {
            return false;
        }
        for (String prefix : SKILL_ROOT_PREFIXES) {
            if (entryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把单个 SKILL.md 入口转换成 {@link SkillDescriptor}.
     */
    @Nullable
    private static SkillDescriptor readDescriptor(@NotNull JarFile jarFile, @NotNull JarEntry entry) {
        String entryName = entry.getName();
        String jarEntryRoot = entryName.substring(0, entryName.length() - SKILL_MD.length());
        String fallbackName = inferNameFromRoot(jarEntryRoot);

        String content;
        try (InputStream in = jarFile.getInputStream(entry)) {
            content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Failed to read SKILL.md entry: " + entryName, e);
            return null;
        }

        ParsedSkillMd parsed = SkillFrontmatterParser.parse(content);
        String name = parsed.getName() != null ? parsed.getName() : fallbackName;

        return new SkillDescriptor(
            name,
            parsed.getDescription(),
            parsed.getAllowedTools(),
            parsed.getLicense(),
            jarEntryRoot,
            entryName,
            parsed.getBody()
        );
    }

    /**
     * 从根目录路径推断 Skill 名称, 例如:
     * {@code META-INF/skills/dev/dong4j/code-review/} -> {@code code-review}.
     */
    @NotNull
    private static String inferNameFromRoot(@NotNull String jarEntryRoot) {
        String trimmed = jarEntryRoot.endsWith("/")
            ? jarEntryRoot.substring(0, jarEntryRoot.length() - 1)
            : jarEntryRoot;
        int slash = trimmed.lastIndexOf('/');
        if (slash < 0) {
            return trimmed;
        }
        return trimmed.substring(slash + 1);
    }

    /**
     * 把 IDEA VirtualFile 转成本地 Jar 路径.
     *
     * <p>IDEA 在 classpath 中给到的 VirtualFile 通常是 {@code file:///.../foo.jar} 或
     * {@code jar:///.../foo.jar!/}, 这里只接受前者; 后者由 {@code JarFileSystem} 表示, 我们通过
     * 协调层把 jar 根目录定位回 jar 文件后再调用本方法, 因此这里只需直接读 path.</p>
     *
     * @param virtualFile Jar 虚拟文件
     * @return 本地路径; 不可访问时返回 null
     */
    @Nullable
    private static Path resolveLocalPath(@NotNull VirtualFile virtualFile) {
        String path = virtualFile.getPath();
        // VirtualFile 可能携带 jar 协议尾部分隔符 "!/", 必须剥掉
        int separator = path.indexOf("!/");
        if (separator >= 0) {
            path = path.substring(0, separator);
        }
        try {
            return Path.of(path);
        } catch (Exception e) {
            LOG.debug("Invalid jar path: " + path, e);
            return null;
        }
    }
}
