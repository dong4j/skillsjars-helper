package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;
import dev.dong4j.idea.skillsjars.helper.util.SkillsJarsHelperBundle;

/**
 * SBT 依赖扫描器.
 *
 * <p>识别 IDEA 中以 {@code "sbt: "} 开头的 Library.</p>
 * <p>注意: Scala 插件(SBT 集成)通常不会把自定义 configuration (如 {@code % Skills}) 映射到 IDEA
 * module 的 dependencies 中, 因此 {@code OrderEnumerator} 看不到这些依赖.
 * 但这些依赖通常会被 SBT 插件解析并注册在项目的 {@code LibraryTable} 中,
 * 所以我们直接遍历 Project 级别的 {@code LibraryTable} 即可找到.</p>
 *
 * @author dong4j
 * @version 1.0.0
 * @since 1.0.0
 */
public final class SbtSkillSourceScanner implements SkillSourceScanner {

    private static final String LIBRARY_NAME_PREFIX = "sbt: ";

    @Override
    @NotNull
    public String getDisplayName() {
        return SkillsJarsHelperBundle.message("scanner.sbt.dependencies");
    }

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        // 只要项目有 sbt 库即可适用
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(context.getProject());
        for (Library library : libraryTable.getLibraries()) {
            String name = library.getName();
            if (name != null && name.startsWith(LIBRARY_NAME_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NotNull
    public List<SkillJarSource> scan(@NotNull ScanContext context) {
        Project project = context.getProject();
        Set<String> visitedJars = new HashSet<>();
        List<SkillJarSource> result = new ArrayList<>();

        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        for (Library library : libraryTable.getLibraries()) {
            context.checkCanceled();
            String name = library.getName();
            if (name == null || !name.startsWith(LIBRARY_NAME_PREFIX)) {
                continue;
            }
            SkillCoordinate coordinate = SkillCoordinate.fromLibraryName(name);
            for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
                context.checkCanceled();
                VirtualFile jarFile = AbstractLibraryScanner.toLocalJarFile(root);
                if (jarFile == null) {
                    continue;
                }
                if (!visitedJars.add(jarFile.getPath())) {
                    continue;
                }
                result.add(new SkillJarSource(jarFile, SkillSourceType.SBT_DEPENDENCY, coordinate, name));
            }
        }
        return result;
    }
}
