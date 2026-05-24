package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * 基于 IDEA Library 的扫描器基类.
 *
 * <p>Maven 和 Gradle 通过 IDEA 导入时, 都会把每个外部依赖建模成一个 {@code Library}, 名称为
 * {@code "Maven: g:a:v"} 或 {@code "Gradle: g:a:v"}. 子类只需要决定 "我接受哪个前缀" 以及
 * "对应的来源类型是什么", 就能复用扫描骨架.</p>
 *
 * <p>该基类故意不直接实现 {@link SkillSourceScanner}, 而是把骨架方法抽出来, 这样 Maven plugin
 * 扫描器走另一条路径 (使用 Maven Project API) 时不会被骨架强制约束.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public abstract class AbstractLibraryScanner implements SkillSourceScanner {

    /**
     * 子类要识别的库名前缀 (含尾部空格), 例如 {@code "Maven: "}.
     *
     * @return 前缀
     */
    @NotNull
    protected abstract String getLibraryNamePrefix();

    /**
     * 该来源对应的 {@link SkillSourceType}.
     *
     * @return 来源类型
     */
    @NotNull
    protected abstract SkillSourceType getSourceType();

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        // 默认只要项目存在 Java 模块就认为适用; 子类可以收紧.
        return ModuleManager.getInstance(context.getProject()).getModules().length > 0;
    }

    @Override
    @NotNull
    public List<SkillJarSource> scan(@NotNull ScanContext context) {
        Project project = context.getProject();
        Set<String> visitedJars = new HashSet<>();
        List<SkillJarSource> result = new ArrayList<>();
        String prefix = this.getLibraryNamePrefix();
        SkillSourceType sourceType = this.getSourceType();

        for (Module module : ModuleManager.getInstance(project).getModules()) {
            context.checkCanceled();
            OrderEnumerator.orderEntries(module).librariesOnly().forEachLibrary(library -> {
                this.collectFromLibrary(library, prefix, sourceType, visitedJars, result, context);
                return true;
            });
        }
        return result;
    }

    /**
     * 把单个 Library 的 jar 根添加到结果. 使用包私有可见性方便子类按需复用.
     */
    private void collectFromLibrary(@NotNull Library library,
                                    @NotNull String namePrefix,
                                    @NotNull SkillSourceType sourceType,
                                    @NotNull Set<String> visitedJars,
                                    @NotNull List<SkillJarSource> result,
                                    @NotNull ScanContext context) {
        String name = library.getName();
        if (name == null || !name.startsWith(namePrefix)) {
            return;
        }
        SkillCoordinate coordinate = SkillCoordinate.fromLibraryName(name);
        for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
            context.checkCanceled();
            VirtualFile jarFile = toLocalJarFile(root);
            if (jarFile == null) {
                continue;
            }
            if (!visitedJars.add(jarFile.getPath())) {
                continue;
            }
            result.add(new SkillJarSource(jarFile, sourceType, coordinate, name));
        }
    }

    /**
     * 把 OrderEnumerator 产出的 Jar 根 (jar://.../foo.jar!/) 还原为本地 jar 文件 (file:///.../foo.jar).
     *
     * <p>OrderEnumerator 的 CLASSES 根通常是 {@code JarFileSystem} 表示的 Jar 根目录, 我们需要
     * 拿到底层的本地文件才能交给 {@code SkillJarParser} 用 {@code JarFile} 打开. 对于非 Jar 类型
     * (例如 directory output) 直接返回 null.</p>
     *
     * @param classesRoot OrderEnumerator 产出的 root
     * @return 本地 jar 的 VirtualFile; 不是 jar 时返回 null
     */
    @Nullable
    static VirtualFile toLocalJarFile(@NotNull VirtualFile classesRoot) {
        // JarFileSystem 提供 getLocalByEntry, 把 jar://..!/ 还原成 file://..
        VirtualFile local = JarFileSystem.getInstance().getLocalByEntry(classesRoot);
        if (local != null) {
            return ensureJarPath(local);
        }
        // 兜底: 直接拿 path, 剥掉 "!/" 然后通过 VFS 找文件
        String path = classesRoot.getPath();
        int sep = path.indexOf("!/");
        if (sep > 0) {
            path = path.substring(0, sep);
        }
        if (!path.endsWith(".jar")) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        return VirtualFileManager.getInstance().findFileByNioPath(file.toPath());
    }

    @Nullable
    private static VirtualFile ensureJarPath(@NotNull VirtualFile file) {
        if (file.getPath().endsWith(".jar")) {
            return file;
        }
        return null;
    }
}
