package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;
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
 * 控制), 因此不需要在运行时再做 ClassLoader 反射处理.</p>
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
        return "Maven Plugin Dependencies";
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
        List<MavenId> dependencies = plugin.getDependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return;
        }
        for (MavenId mavenId : dependencies) {
            context.checkCanceled();
            this.collectMavenId(mavenId, localRepo, visited, result);
        }
    }

    /**
     * 把单个 {@link MavenId} 解析为本地 jar 文件并加入结果.
     */
    private void collectMavenId(@NotNull MavenId mavenId,
                                @NotNull File localRepo,
                                @NotNull Set<String> visited,
                                @NotNull List<SkillJarSource> result) {
        String groupId = mavenId.getGroupId();
        String artifactId = mavenId.getArtifactId();
        String version = mavenId.getVersion();
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
