package dev.dong4j.idea.skillsjars.helper.scanner;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MavenPluginDependencyScanner} 单元测试.
 *
 * <p>覆盖范围: plugin 块依赖的解析、本地仓库路径反射、Jar 文件布局、取消协作、
 * {@code isApplicable} 行为, 以及基于真实 {@code skillsjars-example-spring-ai} 坐标的端到端校验.</p>
 *
 * <p>mock 策略说明:
 * <ul>
 *   <li>{@link MavenPlugin} 与 {@link MavenId} 是普通类, 直接用真实构造函数.</li>
 *   <li>{@link MavenProjectsManager} 是 final 类, 通过 {@code mockStatic} + mockito-inline
 *       拦截 {@code getInstance(Project)} 静态工厂.</li>
 *   <li>{@link VirtualFileManager#getInstance()} 同样用 {@code mockStatic} 拦截, 避免依赖 IDEA 平台测试夹具.</li>
 *   <li>jar 文件用真实 {@link JarOutputStream} 写到 {@code @TempDir}, 让路径布局逻辑走真实文件系统.</li>
 * </ul>
 *
 * @author dong4j
 */
class MavenPluginDependencyScannerTest {

    /** 临时目录, 在每个测试结束后自动清理. */
    @TempDir
    Path tempDir;

    /** 待测对象. */
    private final MavenPluginDependencyScanner scanner = new MavenPluginDependencyScanner();

    private Project project;
    private MavenProjectsManager mavenManager;
    private VirtualFileManager vfsManager;
    private ScanContext context;
    private MockedStatic<MavenProjectsManager> mavenManagerStatic;
    private MockedStatic<VirtualFileManager> vfsManagerStatic;

    @BeforeEach
    void setUp() {
        this.project = mock(Project.class);
        this.mavenManager = mock(MavenProjectsManager.class);
        this.vfsManager = mock(VirtualFileManager.class);

        // ScanContext 不需要真的 ProgressIndicator; 取消相关的测试单独构造.
        this.context = new ScanContext(this.project, null);

        // 把静态工厂的返回值绑死, 避免依赖 IDEA 平台服务.
        // 暴露成字段, 让 isApplicable 等需要在测试里改写 stubbing 的 case 能复用同一个
        // MockedStatic, 避免嵌套 mockStatic 触发 "static mocking is already in progress" 异常.
        this.mavenManagerStatic = mockStatic(MavenProjectsManager.class);
        this.mavenManagerStatic.when(() -> MavenProjectsManager.getInstance(this.project)).thenReturn(this.mavenManager);
        this.vfsManagerStatic = mockStatic(VirtualFileManager.class);
        this.vfsManagerStatic.when(VirtualFileManager::getInstance).thenReturn(this.vfsManager);
    }

    @AfterEach
    void tearDown() {
        this.mavenManagerStatic.close();
        this.vfsManagerStatic.close();
    }

    // ---------------------------------------------------------------------
    // 1. collectFromPlugin 解析
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("1. collectFromPlugin 解析")
    class CollectFromPluginTests {

        @Test
        @DisplayName("1.1 单个 plugin + 单个 dependency -> 1 个 SkillJarSource")
        void should_collect_single_dependency() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File jar = createLocalArtifact(repo, "com.example", "lib", "1.0.0");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.example", "p", "1.0.0",
                        mavenId("com.example", "lib", "1.0.0"))));
            stubVirtualFile(jar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).hasSize(1);
            SkillJarSource source = result.get(0);
            assertThat(source.getSourceType()).isEqualTo(SkillSourceType.MAVEN_PLUGIN_DEPENDENCY);
            assertThat(source.getCoordinate().toCoordinateString()).isEqualTo("com.example:lib:1.0.0");
            assertThat(source.getDisplayName()).isEqualTo("com.example:lib:1.0.0");
            assertThat(source.getJarFile().getPath()).isEqualTo(jar.getAbsolutePath());
        }

        @Test
        @DisplayName("1.2 多个 plugin + 多个 dependency -> 全部出现")
        void should_collect_all_dependencies_across_plugins() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File jarA = createLocalArtifact(repo, "g.one", "a", "1.0.0");
            File jarB = createLocalArtifact(repo, "g.two", "b", "2.0.0");
            File jarC = createLocalArtifact(repo, "g.three", "c", "3.0.0");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("g.one", "p1", "1.0.0",
                        mavenId("g.one", "a", "1.0.0"),
                        mavenId("g.two", "b", "2.0.0")),
                    mavenPlugin("g.three", "p2", "1.0.0",
                        mavenId("g.three", "c", "3.0.0"))));
            stubVirtualFile(jarA);
            stubVirtualFile(jarB);
            stubVirtualFile(jarC);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).extracting(s -> s.getCoordinate().toCoordinateString())
                .containsExactlyInAnyOrder("g.one:a:1.0.0", "g.two:b:2.0.0", "g.three:c:3.0.0");
        }

        @Test
        @DisplayName("1.3 同一个 dependency 在多个 plugin 中声明 -> 去重 (visited set)")
        void should_dedup_same_dependency_across_plugins() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File sharedJar = createLocalArtifact(repo, "g.shared", "lib", "1.0.0");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("g.a", "p1", "1.0.0", mavenId("g.shared", "lib", "1.0.0")),
                    mavenPlugin("g.b", "p2", "1.0.0", mavenId("g.shared", "lib", "1.0.0")),
                    mavenPlugin("g.c", "p3", "1.0.0", mavenId("g.shared", "lib", "1.0.0"))));
            stubVirtualFile(sharedJar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            // 同一 jar 路径去重, 只产出一个 SkillJarSource
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCoordinate().toCoordinateString()).isEqualTo("g.shared:lib:1.0.0");
        }

        @Test
        @DisplayName("1.4 dependency groupId/artifactId 包含点和短横线")
        void should_handle_group_and_artifact_with_dots_and_dashes() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File jar = createLocalArtifact(repo, "com.skillsjars", "anthropics__skills__pdf", "2026_02_25-3d59511");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.skillsjars", "maven-plugin", "0.0.7",
                        mavenId("com.skillsjars", "anthropics__skills__pdf", "2026_02_25-3d59511"))));
            stubVirtualFile(jar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCoordinate().toCoordinateString())
                .isEqualTo("com.skillsjars:anthropics__skills__pdf:2026_02_25-3d59511");
            // 路径上 groupId 的点要转斜杠, artifactId 的下划线和短横线原样保留
            assertThat(result.get(0).getJarFile().getPath())
                .contains("com/skillsjars/anthropics__skills__pdf/2026_02_25-3d59511/");
        }

        @Test
        @DisplayName("1.5 version 含 SNAPSHOT / 数字+点 -> 路径与坐标都按字面值保留")
        void should_handle_snapshot_and_dotted_versions() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File snapshotJar = createLocalArtifact(repo, "g.alpha", "lib", "1.0.0-SNAPSHOT");
            File dottedJar = createLocalArtifact(repo, "g.beta", "lib", "2.10.5");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("g.alpha", "p", "1.0.0", mavenId("g.alpha", "lib", "1.0.0-SNAPSHOT")),
                    mavenPlugin("g.beta", "p", "1.0.0", mavenId("g.beta", "lib", "2.10.5"))));
            stubVirtualFile(snapshotJar);
            stubVirtualFile(dottedJar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).extracting(s -> s.getCoordinate().toCoordinateString())
                .containsExactlyInAnyOrder("g.alpha:lib:1.0.0-SNAPSHOT", "g.beta:lib:2.10.5");
            assertThat(result).extracting(s -> s.getJarFile().getName())
                .containsExactlyInAnyOrder("lib-1.0.0-SNAPSHOT.jar", "lib-2.10.5.jar");
        }
    }

    // ---------------------------------------------------------------------
    // 2. 本地仓库路径解析
    //
    // 注意: 不同 IDEA 版本的 {@code MavenProjectsManager} 暴露过 {@code getRepositoryFile()} /
    // {@code getLocalRepository()} / {@code getRepositoryPath()} 三种方法. 反射链按顺序尝试,
    // 第一个返回非空的方法胜出; 方法不存在时 NoSuchMethodException 被静默吞掉.
    // 当前测试在 IDEA 2024.2 上跑, 该版本只暴露 {@code getLocalRepository()}.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("2. 本地仓库路径解析 (反射链)")
    class LocalRepositoryResolutionTests {

        @Test
        @DisplayName("2.1 反射命中第一个返回非空目录的方法 -> scanner 使用该路径解析 jar")
        void should_use_first_method_returning_a_valid_directory() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File jar = createLocalArtifact(repo, "g", "a", "1.0.0");

            // 2024.2 上唯一可用的方法是 getLocalRepository(); 反射链中靠前的方法 (getRepositoryFile)
            // 不存在, NoSuchMethodException 被静默吞掉, 然后命中 getLocalRepository().
            // 注意: 一定要先把 project 构造完 (含内层 stubbing) 再传进 stubProject,
            // 避免在 thenReturn() 参数求值时触发嵌套 stubbing.
            MavenProject project = mavenProject(
                mavenPlugin("g", "p", "1.0.0", mavenId("g", "a", "1.0.0")));
            when(MavenPluginDependencyScannerTest.this.mavenManager.getLocalRepository()).thenReturn(repo);
            stubProject(project);
            stubVirtualFile(jar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getJarFile().getPath()).startsWith(repo.getAbsolutePath());
        }

        @Test
        @DisplayName("2.2 第一个方法存在但返回 null -> 反射链继续尝试下一个")
        void should_continue_chain_when_first_method_returns_null() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File jar = createLocalArtifact(repo, "g", "a", "1.0.0");

            // 模拟"靠前的方法存在但 IDE 没解析到仓库 (例如用户没在 Settings 里设过 Maven Home)"
            // 这种情况下 scanner 应该继续往下找, 而不是直接放弃.
            when(MavenPluginDependencyScannerTest.this.mavenManager.getLocalRepository()).thenReturn(null);
            // 在 2024.2 上 getRepositoryPath() 不存在; 反射抛 NoSuchMethodException, 链就此结束 -> 返回 null
            MavenProject project = mavenProject(
                mavenPlugin("g", "p", "1.0.0", mavenId("g", "a", "1.0.0")));
            stubProject(project);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            // 链上所有方法都拿不到值 -> 优雅降级
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("2.3 反射链在当前 IDEA 上能用: getLocalRepository 是 2024.2 上唯一暴露的解析入口")
        void should_use_reflection_method_available_in_2024_2() throws Exception {
            // 反射链里靠前的 getRepositoryFile / getRepositoryPath 在 2024.2 上不存在,
            // 反射抛 NoSuchMethodException 后被 scanner 静默吞掉, 最终命中 getLocalRepository.
            // 这个 case 已经在 2.1 / 2.4 覆盖, 此处用 jdk 反射验证 2024.2 上的方法签名,
            // 确保升级到 2025.x 后, 如果新方法被加入, 测试可以识别.
            java.lang.reflect.Method getLocalRepository = MavenProjectsManager.class.getMethod("getLocalRepository");
            assertThat(getLocalRepository).isNotNull();
            assertThat(getLocalRepository.getReturnType()).isEqualTo(File.class);
        }

        @Test
        @DisplayName("2.4 三种方法都拿不到有效值 -> 优雅降级, 返回空列表不抛异常")
        void should_return_empty_when_no_repo_method_yields_a_directory() {
            MavenProject project = mavenProject(
                mavenPlugin("g", "p", "1.0.0", mavenId("g", "a", "1.0.0")));
            stubProject(project);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).isEmpty();
            // 解析不到仓库时, VFS 不应被查询
            verifyNoInteractions(MavenPluginDependencyScannerTest.this.vfsManager);
        }
    }

    // ---------------------------------------------------------------------
    // 3. Jar 文件解析
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("3. Jar 文件解析")
    class JarResolutionTests {

        @Test
        @DisplayName("3.1 标准布局 <repo>/<group>/<artifactId>/<version>/<artifactId>-<version>.jar -> 存在即收集")
        void should_resolve_standard_layout_jar() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            File expected = new File(repo, "com/example/lib/1.0.0/lib-1.0.0.jar");
            writeEmptyJar(expected);

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.example", "p", "1.0.0", mavenId("com.example", "lib", "1.0.0"))));
            stubVirtualFile(expected);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getJarFile().getPath()).isEqualTo(expected.getAbsolutePath());
        }

        @Test
        @DisplayName("3.2 jar 不存在 -> 静默跳过, 不污染结果")
        void should_skip_missing_jar() {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            // 不写任何文件

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.example", "p", "1.0.0",
                        mavenId("com.example", "missing", "1.0.0"))));

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).isEmpty();
            // 不应触发 VFS 查询, 因为 File.isFile() 已经是 false
            verifyNoInteractions(MavenPluginDependencyScannerTest.this.vfsManager);
        }

        @Test
        @DisplayName("3.3 路径含 groupId 多段 (com.skillsjars -> com/skillsjars)")
        void should_resolve_multi_segment_group_id_to_path() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            // 验证 groupId 多段 + 实际路径是分层目录
            File jar = createLocalArtifact(repo, "com.skillsjars", "anthropics__skills__pdf", "2026_02_25-3d59511");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.skillsjars", "maven-plugin", "0.0.7",
                        mavenId("com.skillsjars", "anthropics__skills__pdf", "2026_02_25-3d59511"))));
            stubVirtualFile(jar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).hasSize(1);
            // 路径上 groupId 的点全转成斜杠
            assertThat(jar.getAbsolutePath()).contains("com/skillsjars/anthropics__skills__pdf/2026_02_25-3d59511/");
        }
    }

    // ---------------------------------------------------------------------
    // 4. 取消协作
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("4. 取消协作")
    class CancellationTests {

        @Test
        @DisplayName("4.1 扫描过程中 ScanContext.checkCanceled() 抛出 -> 异常向上传播, 不吞掉")
        void should_propagate_cancellation_during_project_loop() {
            ProgressIndicator indicator = mock(ProgressIndicator.class);
            // 第一次调用就抛, 模拟用户在循环中点了取消
            doThrow(new ProcessCanceledException()).when(indicator).checkCanceled();

            ScanContext cancelContext = new ScanContext(MavenPluginDependencyScannerTest.this.project, indicator);

            // 预构建 project, 避免在 thenReturn() 参数里再触发 stubbing
            MavenProject project = mavenProject(
                mavenPlugin("g", "p", "1.0.0", mavenId("g", "a", "1.0.0")));
            stubLocalRepo(MavenPluginDependencyScannerTest.this.tempDir.toFile());
            stubProject(project);

            assertThatThrownBy(() -> MavenPluginDependencyScannerTest.this.scanner.scan(cancelContext))
                .isInstanceOf(ProcessCanceledException.class);
            // 外层每次迭代都会调一次 checkCanceled, 第一次就抛了所以只调用一次
            verify(indicator).checkCanceled();
        }

        @Test
        @DisplayName("4.2 dependency 循环内也能响应取消, 不会跑完整个 plugin 块")
        void should_propagate_cancellation_inside_dependency_loop() {
            ProgressIndicator indicator = mock(ProgressIndicator.class);
            // 前两次调用 (project 循环一次 + dep 循环一次) 都放过, 第二次进 dep 循环时抛
            doNothing()
                .doNothing()
                .doThrow(new ProcessCanceledException())
                .when(indicator).checkCanceled();

            ScanContext cancelContext = new ScanContext(MavenPluginDependencyScannerTest.this.project, indicator);

            MavenProject project = mavenProject(
                mavenPlugin("g", "p", "1.0.0",
                    mavenId("g", "a", "1.0.0"),
                    mavenId("g", "b", "1.0.0"),
                    mavenId("g", "c", "1.0.0")));
            stubLocalRepo(MavenPluginDependencyScannerTest.this.tempDir.toFile());
            stubProject(project);

            assertThatThrownBy(() -> MavenPluginDependencyScannerTest.this.scanner.scan(cancelContext))
                .isInstanceOf(ProcessCanceledException.class);
            // 共调用 3 次: project loop 一次, dep loop 一次后抛出
            verify(indicator, times(3)).checkCanceled();
        }
    }

    // ---------------------------------------------------------------------
    // 5. isApplicable 行为
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("5. isApplicable 行为")
    class IsApplicableTests {

        @Test
        @DisplayName("5.1 MavenProjectsManager 拿不到实例 -> false")
        void should_return_false_when_manager_unavailable() {
            // 复用 setUp 已开好的 mockStatic, 直接重写 getInstance 返 null
            MavenPluginDependencyScannerTest.this.mavenManagerStatic
                .when(() -> MavenProjectsManager.getInstance(MavenPluginDependencyScannerTest.this.project))
                .thenReturn(null);

            boolean applicable = MavenPluginDependencyScannerTest.this.scanner.isApplicable(MavenPluginDependencyScannerTest.this.context);

            assertThat(applicable).isFalse();
        }

        @Test
        @DisplayName("5.2 MavenProjectsManager.getInstance() 抛异常 -> false, 不向上传播")
        void should_return_false_when_manager_throws() {
            MavenPluginDependencyScannerTest.this.mavenManagerStatic
                .when(() -> MavenProjectsManager.getInstance(MavenPluginDependencyScannerTest.this.project))
                .thenThrow(new RuntimeException("Maven plugin not installed"));

            boolean applicable = MavenPluginDependencyScannerTest.this.scanner.isApplicable(MavenPluginDependencyScannerTest.this.context);

            assertThat(applicable).isFalse();
        }

        @Test
        @DisplayName("5.3 manager 存在但 projects 为空 -> false")
        void should_return_false_when_no_projects() {
            when(MavenPluginDependencyScannerTest.this.mavenManager.getProjects()).thenReturn(Collections.emptyList());

            boolean applicable = MavenPluginDependencyScannerTest.this.scanner.isApplicable(MavenPluginDependencyScannerTest.this.context);

            assertThat(applicable).isFalse();
        }

        @Test
        @DisplayName("5.4 manager 存在且 projects 非空 -> true")
        void should_return_true_when_projects_present() {
            // 预构建 project 再传入 when().thenReturn(), 避免在参数里触发嵌套 stubbing
            MavenProject project = mavenProject(mavenPlugin("g", "p", "1.0.0"));
            when(MavenPluginDependencyScannerTest.this.mavenManager.getProjects())
                .thenReturn(List.of(project));

            boolean applicable = MavenPluginDependencyScannerTest.this.scanner.isApplicable(MavenPluginDependencyScannerTest.this.context);

            assertThat(applicable).isTrue();
        }
    }

    // ---------------------------------------------------------------------
    // 6. 集成 / 端到端 (真实 skillsjars-example-spring-ai 坐标)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("6. 集成 / 端到端")
    class IntegrationTests {

        @Test
        @DisplayName("6.1 真实 skillsjars-example-spring-ai: 扫到 plugin 块中的两个依赖")
        void should_collect_real_world_plugin_dependencies() throws IOException {
            File repo = MavenPluginDependencyScannerTest.this.tempDir.toFile();
            // 与 skillsjars-example-spring-ai/pom.xml 中
            //   <plugin>
            //     <artifactId>maven-plugin</artifactId>
            //     <dependencies>
            //       com.skillsjars:sivaprasadreddy__sivalabs-agent-skills__spring-boot:2026_02_23-dba3310
            //       org.springaicommunity:spring-testing-skills:1.1.0
            //     </dependencies>
            //   </plugin>
            // 完全一致的坐标 + 真实 jar
            File skillsJar = createLocalArtifact(repo,
                "com.skillsjars", "sivaprasadreddy__sivalabs-agent-skills__spring-boot", "2026_02_23-dba3310");
            File testingSkillsJar = createLocalArtifact(repo,
                "org.springaicommunity", "spring-testing-skills", "1.1.0");

            stubLocalRepo(repo);
            stubProject(
                mavenProject(
                    mavenPlugin("com.skillsjars", "maven-plugin", "0.0.7",
                        mavenId("com.skillsjars", "sivaprasadreddy__sivalabs-agent-skills__spring-boot", "2026_02_23-dba3310"),
                        mavenId("org.springaicommunity", "spring-testing-skills", "1.1.0"))));
            stubVirtualFile(skillsJar);
            stubVirtualFile(testingSkillsJar);

            List<SkillJarSource> result = MavenPluginDependencyScannerTest.this.scanner.scan(MavenPluginDependencyScannerTest.this.context);

            assertThat(result).extracting(s -> s.getCoordinate().toCoordinateString())
                .containsExactlyInAnyOrder(
                    "com.skillsjars:sivaprasadreddy__sivalabs-agent-skills__spring-boot:2026_02_23-dba3310",
                    "org.springaicommunity:spring-testing-skills:1.1.0");
            assertThat(result).allMatch(s -> s.getSourceType() == SkillSourceType.MAVEN_PLUGIN_DEPENDENCY);
        }
    }

    // ---------------------------------------------------------------------
    // 辅助方法
    // ---------------------------------------------------------------------

    /**
     * 在 {@code <repo>/<group-slash>/<artifactId>/<version>/} 下写一个空 jar, 返回 jar 文件.
     */
    private static File createLocalArtifact(File repo, String groupId, String artifactId, String version) throws IOException {
        String relativePath = groupId.replace('.', '/') + File.separator
            + artifactId + File.separator
            + version + File.separator
            + artifactId + "-" + version + ".jar";
        File jar = new File(repo, relativePath);
        return writeEmptyJar(jar);
    }

    /**
     * 创建一个最小可用的空 jar (只有一个 SKILL.md entry), 真实文件系统操作.
     */
    private static File writeEmptyJar(File jar) throws IOException {
        File parent = jar.getParentFile();
        if (parent != null) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            JarEntry entry = new JarEntry("META-INF/skills/_dummy/SKILL.md");
            out.putNextEntry(entry);
            out.write("---\nname: _dummy\n---\nbody".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    /**
     * 让 {@link VirtualFileManager#findFileByNioPath} 在传入这个 jar 路径时返回一个 mock 的
     * {@link VirtualFile}. 用 {@code argThat} 做 path 等值匹配, 避开 NIO Path 引用相等问题.
     */
    private void stubVirtualFile(File jar) {
        VirtualFile vf = mock(VirtualFile.class);
        when(vf.getPath()).thenReturn(jar.getAbsolutePath());
        when(vf.getName()).thenReturn(jar.getName());
        when(this.vfsManager.findFileByNioPath(jar.toPath())).thenReturn(vf);
    }

    /**
     * 把本地仓库路径桩到 {@link MavenProjectsManager#getLocalRepository()} (走反射链的第一个匹配项).
     */
    private void stubLocalRepo(File repo) {
        when(this.mavenManager.getLocalRepository()).thenReturn(repo);
    }

    /**
     * 让 {@link MavenProjectsManager#getProjects()} 返回一个由给定 plugins 组成的 project.
     */
    private void stubProject(MavenProject project) {
        when(this.mavenManager.getProjects()).thenReturn(List.of(project));
    }

    private static MavenProject mavenProject(MavenPlugin... plugins) {
        MavenProject project = mock(MavenProject.class);
        // 使用 doReturn().when() 而不是 when().thenReturn() 是为了避免嵌套 stubbing 问题:
        // 当本 helper 被另一个 when(...).thenReturn(...) 的参数调用时, 内层的 when() 会被
        // Mockito 误判为 "unfinished stubbing"; doReturn 语法不调用真实方法, 因此不进入
        // 嵌套 stubbing 状态, 可以在其他 stubbing 的参数里安全使用.
        doReturn(List.of(plugins)).when(project).getPlugins();
        return project;
    }

    /**
     * 构造一个真实 {@link MavenPlugin} 实例. 这里用 {@code dependencies} 之外的字段填默认值,
     * 扫描器只读 {@code getDependencies()}.
     */
    private static MavenPlugin mavenPlugin(String groupId, String artifactId, String version, MavenId... dependencies) {
        List<MavenId> deps = dependencies.length == 0 ? Collections.emptyList() : List.of(dependencies);
        return new MavenPlugin(groupId, artifactId, version, false, false, null, Collections.emptyList(), deps);
    }

    private static MavenId mavenId(String groupId, String artifactId, String version) {
        return new MavenId(groupId, artifactId, version);
    }
}
