package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;
import dev.dong4j.idea.skillsjars.helper.util.SkillsJarsHelperBundle;

/**
 * Maven 插件依赖扫描器.
 *
 * <p>SkillsJars 官方推荐的一种使用方式是把 SkillsJar 写在 {@code skillsjars-maven-plugin} 自身的
 * {@code <dependencies>} 块, 这种依赖<strong>不会</strong>出现在项目 classpath, 因此基于
 * {@code OrderEnumerator} 的 {@link MavenLibraryScanner} 看不到它. 该扫描器通过 IDEA 的
 * Maven 集成 ({@code org.jetbrains.idea.maven}) 直接读取 {@code MavenProject#getPlugins()},
 * 把每个 plugin 的 dependencies 解析为本地 Jar.</p>
 *
 * <p>注意: 该扫描器仅在 IDEA 启用了 Maven 插件时才会注册 (通过 plugin.xml 中
 * {@code <depends optional="true" config-file="skillsjars-maven.xml">org.jetbrains.idea.maven</depends>}
 * 控制). Maven 坐标对象在不同平台版本中由不同模块提供，因此只对坐标 getter 使用反射，避免把不稳定类型写入插件字节码签名.</p>
 *
 * <p>当前实现细节:</p>
 * <ul>
 *   <li>遍历所有 {@code MavenProject} 的 {@code getPlugins()}, 不限定特定的 plugin groupId/artifactId,
 *       因为社区里也有非官方的 SkillsJar 集成插件; 由后续解析阶段过滤掉不含 SKILL.md 的 Jar.</li>
 *   <li>按 Maven 本地仓库标准布局直接拼路径定位 jar (避免不同版本 IDEA Maven API 的差异).</li>
 *   <li>同一坐标可能在多个 plugin 中重复出现, 通过 jar 路径集合去重.</li>
 * </ul>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class MavenPluginDependencyScanner implements SkillSourceScanner {

    private static final Logger LOG = Logger.getInstance(MavenPluginDependencyScanner.class);

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillsJarsHelperBundle.message("scanner.maven.plugin.dependencies");
    }

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        Project project = context.getProject();
        try {
            MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
            return manager != null && !manager.getProjects().isEmpty();
        } catch (Throwable t) {
            LOG.debug("Maven plugin not available", t);
            return false;
        }
    }

    @Override
    @NotNull
    public List<SkillJarSource> scan(@NotNull ScanContext context) {
        Project project = context.getProject();
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        if (manager == null) {
            return List.of();
        }

        List<MavenProject> projects = manager.getProjects();
        if (projects.isEmpty()) {
            return List.of();
        }

        File localRepo = resolveLocalRepository(manager);
        if (localRepo == null || !localRepo.isDirectory()) {
            LOG.debug("Maven local repository not configured: " + localRepo);
            return List.of();
        }

        Set<String> visited = new HashSet<>();
        List<SkillJarSource> result = new ArrayList<>();
        for (MavenProject mavenProject : projects) {
            context.checkCanceled();
            for (MavenPlugin plugin : mavenProject.getPlugins()) {
                this.collectFromPlugin(plugin, localRepo, visited, result, context);
            }
        }
        return result;
    }

    /**
     * 把单个插件的 {@code <dependencies>} 收集为候选 Jar.
     *
     * @param plugin    Maven 插件描述
     * @param localRepo 本地仓库根路径
     * @param visited   已访问 jar 路径 (去重)
     * @param result    结果列表
     * @param context   扫描上下文
     */
    private void collectFromPlugin(@NotNull MavenPlugin plugin,
                                   @NotNull File localRepo,
                                   @NotNull Set<String> visited,
                                   @NotNull List<SkillJarSource> result,
                                   @NotNull ScanContext context) {
        List<?> dependencies = plugin.getDependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (Object mavenId : dependencies) {
            context.checkCanceled();
            this.collectMavenId(mavenId, localRepo, visited, result);
        }
    }

    /**
     * 把单个 Maven 坐标对象解析为本地 jar 文件并加入结果.
     * <p> 参数使用 {@link Object} 是为了兼容 IntelliJ Platform 2026.1 的 Maven 模块拆分，
     * 避免生产字节码直接依赖该版本无法解析的 {@code MavenId} 类型.</p>
     */
    private void collectMavenId(@NotNull Object mavenId,
                                @NotNull File localRepo,
                                @NotNull Set<String> visited,
                                @NotNull List<SkillJarSource> result) {
        String groupId = readCoordinatePart(mavenId, "getGroupId");
        String artifactId = readCoordinatePart(mavenId, "getArtifactId");
        String version = readCoordinatePart(mavenId, "getVersion");
        if (groupId == null || artifactId == null || version == null) {
            return;
        }

        File jarFile = resolveArtifactFile(localRepo, groupId, artifactId, version);
        if (!jarFile.isFile()) {
            return;
        }

        if (!visited.add(jarFile.getAbsolutePath())) {
            return;
        }

        VirtualFile vFile = VirtualFileManager.getInstance().findFileByNioPath(jarFile.toPath());
        if (vFile == null) {
            return;
        }

        SkillCoordinate coordinate = SkillCoordinate.of(groupId, artifactId, version);
        result.add(new SkillJarSource(
            vFile,
            SkillSourceType.MAVEN_PLUGIN_DEPENDENCY,
            coordinate,
            coordinate.toCoordinateString()
        ));
    }

    /**
     * 从 Maven 坐标对象读取指定字段
     * <p> 仅在 Maven 插件已加载时调用；反射边界用于隔离 2026.1 的 Maven 模块类型变更，
     * getter 名称在当前支持范围内保持稳定.</p>
     *
     * @param coordinate Maven 坐标对象
     * @param getterName 坐标 getter 名称
     * @return 字符串字段值；类型或方法不匹配时返回 null
     */
    @org.jetbrains.annotations.Nullable
    private static String readCoordinatePart(@NotNull Object coordinate, @NotNull String getterName) {
        try {
            Object value = coordinate.getClass().getMethod(getterName).invoke(coordinate);
            return value instanceof String text ? text : null;
        } catch (ReflectiveOperationException e) {
            LOG.debug("Unable to read Maven coordinate via " + getterName, e);
            return null;
        }
    }

    /**
     * 按 Maven 本地仓库标准布局解析 jar 路径.
     *
     * <p>布局: {@code <repo>/<groupId-with-slash>/<artifactId>/<version>/<artifactId>-<version>.jar}.
     * 故意不依赖 IDEA Maven 工具类, 因为不同版本 IDEA 的 {@code MavenArtifactUtil} 签名差异较大,
     * 且这里的语义足够固定.</p>
     */
    @NotNull
    private static File resolveArtifactFile(@NotNull File localRepo,
                                            @NotNull String groupId,
                                            @NotNull String artifactId,
                                            @NotNull String version) {
        File dir = new File(localRepo, groupId.replace('.', '/'));
        dir = new File(dir, artifactId);
        dir = new File(dir, version);
        return new File(dir, artifactId + "-" + version + ".jar");
    }

    /**
     * 兼容不同版本 IDEA Maven API.
     *
     * <p>不同 IDE 版本的 {@code MavenProjectsManager} 暴露过 {@code getLocalRepository()}/
     * {@code getRepositoryFile()}/{@code getRepositoryPath()} 三种风格 (File / Path / 已废弃),
     * 这里通过反射依次尝试, 既适配老版本也适配 2024.2 之后的新版本.</p>
     *
     * @param manager Maven Project 管理器
     * @return 本地仓库根目录; 都拿不到返回 null
     */
    @org.jetbrains.annotations.Nullable
    private static File resolveLocalRepository(@NotNull MavenProjectsManager manager) {
        for (String method : new String[]{"getRepositoryFile", "getLocalRepository", "getRepositoryPath"}) {
            File file = invokeRepoMethod(manager, method);
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static File invokeRepoMethod(@NotNull MavenProjectsManager manager, @NotNull String methodName) {
        try {
            Object value = MavenProjectsManager.class.getMethod(methodName).invoke(manager);
            if (value instanceof File f) {
                return f;
            }
            if (value instanceof java.nio.file.Path p) {
                return p.toFile();
            }
        } catch (Throwable ignored) {
            // 当前 IDEA 版本没有这个方法, 静默继续尝试下一个
        }
        return null;
    }
}
