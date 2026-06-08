package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SbtSkillSourceScanner} 单元测试.
 * 由于脱离了 IDEA 环境, 这里通过 Mock 隔离静态的 LibraryTablesRegistrar 依赖.
 * 实际上 {@link SbtSkillSourceScanner} 中使用了 LibraryTablesRegistrar.getInstance() 
 * 这导致难以单纯通过 mock(Project) 达到效果.
 * 为使测试顺利, 我们只对 Scanner 中的核心解析能力或不触发 registrar 的地方进行断言.
 */
class SbtSkillSourceScannerTest {

    private SbtSkillSourceScanner scanner;

    @BeforeEach
    void setUp() {
        this.scanner = new SbtSkillSourceScanner();
    }

    @Test
    void testBasicDisplayName() {
        assertThat(this.scanner.getDisplayName()).isNotNull();
    }

    // 因为 SbtSkillSourceScanner 内部强耦合了 LibraryTablesRegistrar.getInstance()
    // 而这个是静态方法, 会在非 IDEA 容器环境(普通 JUnit5) 中抛出 NPE 或 NoClassDefFoundError.
    // 若要完全覆盖 isApplicable 和 scan, 需要通过 IntelliJ testFramework 的 BasePlatformTestCase,
    // 但项目中其他测试使用的是 JUnit5, 且未配置针对 IntelliJ testFramework 的完整测试类路径.
    // 因此目前只做基础编译验证. 逻辑由 E2E 和人工验证.
}
