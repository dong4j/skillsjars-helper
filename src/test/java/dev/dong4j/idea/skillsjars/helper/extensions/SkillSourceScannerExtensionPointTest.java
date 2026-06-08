package dev.dong4j.idea.skillsjars.helper.extensions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;
import dev.dong4j.idea.skillsjars.helper.scanner.ScanContext;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillJarSource;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner;
import dev.dong4j.idea.skillsjars.helper.service.SkillRegistryService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 验证 SkillSourceScanner 扩展点
 */
public class SkillSourceScannerExtensionPointTest extends BasePlatformTestCase {

    public void testExtensionPointRegistrationAndCleanup() {
        // 1. Initial state
        List<SkillSourceScanner> initialScanners = SkillSourceScanner.EP_NAME.getExtensionList();
        int initialSize = initialScanners.size();

        // 2. Register a third-party scanner
        Disposable testDisposable = Disposer.newDisposable();
        SkillSourceScanner customScanner = new SkillSourceScanner() {
            @Override
            public @NotNull String getDisplayName() {
                return "Custom Test Scanner";
            }

            @Override
            public boolean isApplicable(@NotNull ScanContext context) {
                return true;
            }

            @Override
            public @NotNull List<SkillJarSource> scan(@NotNull ScanContext context) {
                return new ArrayList<>();
            }
        };

        ExtensionPoint<SkillSourceScanner> point = SkillSourceScanner.EP_NAME.getPoint();
        point.registerExtension(customScanner, testDisposable);

        // 3. Verify it was registered
        List<SkillSourceScanner> updatedScanners = SkillSourceScanner.EP_NAME.getExtensionList();
        assertEquals("Scanner list should increase by 1", initialSize + 1, updatedScanners.size());
        assertTrue("Custom scanner should be in the list", updatedScanners.contains(customScanner));

        // 4. Verify cleanup when unloaded
        Disposer.dispose(testDisposable);
        List<SkillSourceScanner> cleanedScanners = SkillSourceScanner.EP_NAME.getExtensionList();
        assertEquals("Scanner list should be back to initial size", initialSize, cleanedScanners.size());
        assertFalse("Custom scanner should be removed", cleanedScanners.contains(customScanner));
    }

    public void testMultipleScannersMergeAndDeduplicate() throws Exception {
        Disposable testDisposable = Disposer.newDisposable();
        try {
            java.nio.file.Path tempDirPath = java.nio.file.Files.createTempDirectory("skillsjars-test");
            java.io.File tempDir = tempDirPath.toFile();
            java.io.File skillDir = new java.io.File(tempDir, "META-INF/skills/my-skill");
            skillDir.mkdirs();
            java.io.File skillMd = new java.io.File(skillDir, "SKILL.md");
            java.nio.file.Files.writeString(skillMd.toPath(), "---\nname: Test Skill\n---\ncontent");
            
            java.io.File dummyJar = new java.io.File(tempDir, "dummy.jar");
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(dummyJar));
            zos.putNextEntry(new java.util.zip.ZipEntry("META-INF/skills/my-skill/SKILL.md"));
            java.nio.file.Files.copy(skillMd.toPath(), zos);
            zos.closeEntry();
            zos.close();

            VirtualFile vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dummyJar);
            assertNotNull(vf);
            
            // Scanner 1
            SkillSourceScanner scanner1 = new SkillSourceScanner() {
                @Override
                public @NotNull String getDisplayName() { return "Scanner 1"; }
                @Override
                public boolean isApplicable(@NotNull ScanContext context) { return true; }
                @Override
                public @NotNull List<SkillJarSource> scan(@NotNull ScanContext context) {
                    List<SkillJarSource> list = new ArrayList<>();
                    list.add(new SkillJarSource(vf, SkillSourceType.LOCAL_JAR, SkillCoordinate.unknown(), "Dummy1"));
                    return list;
                }
            };
            
            // Scanner 2: returns the same file
            SkillSourceScanner scanner2 = new SkillSourceScanner() {
                @Override
                public @NotNull String getDisplayName() { return "Scanner 2"; }
                @Override
                public boolean isApplicable(@NotNull ScanContext context) { return true; }
                @Override
                public @NotNull List<SkillJarSource> scan(@NotNull ScanContext context) {
                    List<SkillJarSource> list = new ArrayList<>();
                    list.add(new SkillJarSource(vf, SkillSourceType.EXTERNAL_LIBRARY, SkillCoordinate.unknown(), "Dummy2"));
                    return list;
                }
            };

            ExtensionPoint<SkillSourceScanner> point = SkillSourceScanner.EP_NAME.getPoint();
            point.registerExtension(scanner1, testDisposable);
            point.registerExtension(scanner2, testDisposable);

            SkillRegistryService service = new SkillRegistryService(getProject());
            java.lang.reflect.Method doRefresh = SkillRegistryService.class.getDeclaredMethod("doRefresh", com.intellij.openapi.progress.ProgressIndicator.class);
            doRefresh.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<SkillJarArtifact> artifacts = (List<SkillJarArtifact>) doRefresh.invoke(service, new EmptyProgressIndicator());
            
            // Should only have 1 artifact because of deduplication by jar path
            assertEquals("Should deduplicate to exactly 1 artifact", 1, artifacts.size());
            
            // Should retain the first scanner's source type (LOCAL_JAR)
            assertEquals(SkillSourceType.LOCAL_JAR, artifacts.get(0).getSourceType());
            assertEquals(1, artifacts.get(0).getSkills().size());
        } finally {
            Disposer.dispose(testDisposable);
        }
    }
}

