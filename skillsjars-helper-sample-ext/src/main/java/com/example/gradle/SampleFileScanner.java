package com.example.gradle;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;
import dev.dong4j.idea.skillsjars.helper.scanner.ScanContext;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillJarSource;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class SampleFileScanner implements SkillSourceScanner {

    @Override
    public @NotNull String getDisplayName() {
        return "Sample Local Dir Scanner";
    }

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        String basePath = context.getProject().getBasePath();
        if (basePath == null) {
            return false;
        }
        File localDir = new File(basePath, ".skillsjars-local");
        return localDir.exists() && localDir.isDirectory();
    }

    @Override
    public @NotNull List<SkillJarSource> scan(@NotNull ScanContext context) {
        List<SkillJarSource> sources = new ArrayList<>();
        String basePath = context.getProject().getBasePath();
        if (basePath == null) {
            return sources;
        }
        File localDir = new File(basePath, ".skillsjars-local");
        if (localDir.exists() && localDir.isDirectory()) {
            File[] jars = localDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.isFile() && pathname.getName().endsWith(".jar");
                }
            });
            if (jars != null) {
                for (File jar : jars) {
                    context.checkCanceled();
                    VirtualFile vf = LocalFileSystem.getInstance().findFileByIoFile(jar);
                    if (vf != null) {
                        sources.add(new SkillJarSource(vf, SkillSourceType.LOCAL_JAR, SkillCoordinate.unknown(), jar.getName()));
                    }
                }
            }
        }
        return sources;
    }
}

