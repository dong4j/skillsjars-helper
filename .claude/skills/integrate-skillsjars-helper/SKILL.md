---
name: integrate-skillsjars-helper
description: 在第三方 IntelliJ Platform 插件中接入 SkillsJars Helper 的扫描结果 / 导出能力 / 扫描器扩展点. Use when 用户提到 "对接 SkillsJars Helper" / "调用 SkillRegistry" / "实现 skillSourceScanner" / "订阅 SkillRegistryListener" / "导出 skill 到 Agent 目录" / "在我的插件里使用 SkillsJars Helper", 或要在另一个 IntelliJ 插件项目里复用 SkillsJars Helper 的 API.
---

# /integrate-skillsjars-helper — 接入 SkillsJars Helper

为**第三方 IntelliJ 插件**接入 SkillsJars Helper 的公共能力. 本 skill 是 AI 友好的快速实施指南 (人类完整文档见 [docs/extension-points.md](../../../docs/extension-points.md)).

> 注意: 本 skill 用于"你正在开发**另一个**插件, 想读 / 用 SkillsJars Helper 的数据". 如果你是要修改 SkillsJars Helper 自身, 走 [AGENTS.md](../../../AGENTS.md) §4 入口表.

---

## TL;DR — 你要做什么?

```
┌────────────────────────────────────────────────────────────────────┐
│  你的需求是什么?                                                    │
├────────────────────────────────────────────────────────────────────┤
│  读取当前项目已扫描的 skill 列表 ───────► §2 调 SkillRegistry        │
│                                                                     │
│  在自己 UI 里跟随 skill 列表刷新 ──────► §3 订阅 SkillRegistryListener│
│                                                                     │
│  自动把 skill 导出到 Agent 目录 ───────► §4 调 SkillExportService    │
│                                                                     │
│  接入 Gradle/SBT/自研构建工具的 jar ───► §5 实现 skillSourceScanner  │
└────────────────────────────────────────────────────────────────────┘
```

**唯一不变量**: SkillsJars Helper 的 `api/` 包是公共契约, 永远不会做破坏性变更 (新增 OK, 删除 / 改签名 / 改语义会升 minor 版本并写迁移说明). 其他包 (`service/` / `parser/` / `export/` / `scanner/` 内部实现) **不是**公共 API, 不要反射访问.

---

## 1. 在你的插件里依赖 SkillsJars Helper

### 1.1 Gradle (推荐 IntelliJ Platform Gradle Plugin 2.x)

```kotlin
intellijPlatform {
    plugins("dev.dong4j.idea.skillsjars.helper:<version>")
}
```

> 用最新发布的版本; 没有指定 channel 时默认从 marketplace default channel 拉取.

### 1.2 plugin.xml

```xml
<!-- optional=true 让用户没装 SkillsJars Helper 时你的插件仍能加载; config-file 隔离扩展点声明 -->
<depends optional="true" config-file="skillsjars-integration.xml">
    dev.dong4j.idea.skillsjars.helper
</depends>
```

**如果你只是消费 API (不实现扩展点), 不需要 `config-file`**:

```xml
<depends optional="true">dev.dong4j.idea.skillsjars.helper</depends>
```

`config-file` (skillsjars-integration.xml) 只在你要**注册自己的 skillSourceScanner** 或者其他依赖宿主存在才能加载的扩展点时才需要.

---

## 2. 读取扫描结果

```java
import dev.dong4j.idea.skillsjars.helper.api.SkillRegistry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

SkillRegistry registry = SkillRegistry.getInstance(project);

// 同步读取快照 (只读, 不触发扫描)
List<SkillJarArtifact> artifacts = registry.getArtifacts();

// 扁平获取所有 skill
List<SkillDescriptor> allSkills = registry.getSkills();

// 按名称查找 (大小写敏感, 可能多结果)
List<SkillDescriptor> codeReviews = registry.findByName("code-review");

// 按坐标查找; coordinate 可以是 "groupId:artifactId" (匹配所有版本)
// 或 "groupId:artifactId:version" (精确匹配)
List<SkillJarArtifact> zeka = registry.findByCoordinate("dev.dong4j:zeka-skills");

// 主动触发后台刷新; 完成后会派发 SkillRegistryListener (见 §3)
registry.refresh();
```

**关键约束**:

| 方法 | 线程 | 返回 |
|---|---|---|
| `getArtifacts()` / `getSkills()` / `findBy*()` | 任意线程, 无锁 | 不可变 `List`, 永不为 null |
| `refresh()` | 任意线程, 立即返回 | void (异步) |
| `awaitRefresh()` | **测试专用**, 不要在 UI / 业务用 | void, 同步等到当前刷新完 |

**第一次调用时如果还没扫过, 返回空列表是正常现象**. 主动 `refresh()` 或订阅 listener 等下一次刷新.

---

## 3. 订阅扫描结果变更

```java
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

SkillRegistry registry = SkillRegistry.getInstance(project);

Disposable subscription = registry.addListener(snapshot -> {
    // 在 EDT 上回调; 重活请自行 ApplicationManager.executeOnPooledThread()
    myUi.update(snapshot);
});

// 必须: 把订阅与你的组件生命周期绑定, 否则会泄漏
Disposer.register(parentDisposable, subscription);
```

**回调时机**: 每次 `refresh()` 完成后. 同一份快照可能被派发多次, 不要依赖 "事件来了一定有变化".

**异常被吞**: 你的 listener 抛 RuntimeException 会被 SkillsJars Helper `catch + LOG.warn`, 不会中断其他订阅者. 你应该自己做有意义的错误处理.

---

## 4. 自动导出 Skill 到 Agent 目录

```java
import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.model.*;

SkillExportService export = SkillExportService.getInstance(project);

// 1. 拿到当前项目所有预设 Agent 目录候选
List<SkillTargetDirectory> targets = export.detectTargets(project);
SkillTargetDirectory claude = targets.stream()
    .filter(t -> SkillTargetDirectory.AGENT_CLAUDE.equals(t.getAgentId()))
    .findFirst().orElseThrow();

// 2. 计算执行计划 (只读, 不写盘; 做哈希比对 + 6 状态判定)
ExportPlan plan = export.planExport(jar, skill, claude);

// 3. 按状态决定 UI 行为
switch (plan.getStatus()) {
    case NEW, OUTDATED -> {
        ExportResult r = export.execute(plan);
        if (!r.isSuccess()) showError(r.getErrorMessage());
    }
    case UP_TO_DATE -> {
        // 跳过, 已是最新
    }
    case LOCALLY_MODIFIED, FOREIGN -> {
        if (confirmDestructive()) export.execute(plan);
    }
    case DUPLICATE_NAME -> {
        // 同名冲突, 用户可选: 覆盖原 skill / 改用 fallback 名 / 取消
        // fallback 名: ExportNaming.duplicateFallbackDirectoryName(skill, coord)
        // 但 ExportNaming 不是公共 API, 你需要自己拼: name + "__" + artifactId
        ExportPlan renamed = plan.withTargetDirectoryName(skill.getName() + "__" + jar.getCoordinate().getArtifactId());
        export.execute(renamed);
    }
}
```

### 4.1 InstallationStatus 6 状态语义

| 状态 | 触发条件 | 建议 UI |
|---|---|---|
| `NEW` | 目标目录不存在 | 直接 execute |
| `UP_TO_DATE` | manifest 一致, jar sha + 文件 sha 全对 | 跳过, 通知"已是最新" |
| `OUTDATED` | manifest 来源一致但 jar 升级了, 本地没动 | 直接 execute (覆盖) |
| `LOCALLY_MODIFIED` | manifest 来源一致, 本地文件改了 | 二次确认后 execute |
| `FOREIGN` | 目录存在但没 manifest (用户手工建的) | 二次确认后 execute |
| `DUPLICATE_NAME` | manifest 存在但来源是另一个 artifact | 三选: 覆盖 / 改后缀 / 取消 |

**6 个状态值是契约**, 已发布的不会删, 新增项只会加在末尾. 你的 switch 应该有 `default` 兜底.

### 4.2 订阅安装状态变化

```java
Disposable sub = export.addInstallationListener(src -> {
    // 任何 skill 被导出 / 卸载 / 重算后回调, 在 EDT 上触发
    myInstalledBadge.refresh();
});
Disposer.register(parent, sub);
```

事件粒度刻意保持粗 (不区分单 / 批量, 不带 diff). 监听者应该重新查询当前需要展示的状态.

---

## 5. 接入自定义 Skill 来源 (扩展点)

### 5.1 何时需要

只在以下场景需要实现 `SkillSourceScanner`:
- 你的项目用非 Maven/Gradle 的构建工具 (SBT / 企业自研 IDE 工具集)
- 你想从 `~/.m2/local-skills/` 之类的非标位置扫描本地 Jar
- 你想接入企业内部的 artifact 仓库

**默认情况下不需要**. SkillsJars Helper 内置的 `MavenLibraryScanner` + `MavenPluginDependencyScanner` 已覆盖 95% 场景.

### 5.2 实现骨架

```java
package com.example.scanner;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner;
import dev.dong4j.idea.skillsjars.helper.scanner.ScanContext;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillJarSource;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

public final class MySbtSkillScanner implements SkillSourceScanner {

    @NotNull
    @Override
    public String getDisplayName() {
        return "SBT Dependencies";
    }

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        // 这个方法每次刷新都会被调用; 不要做 IO, 用内存判断
        return MySbtProjectManager.getInstance(context.getProject()).hasProject();
    }

    @NotNull
    @Override
    public List<SkillJarSource> scan(@NotNull ScanContext context) {
        List<SkillJarSource> out = new ArrayList<>();
        for (MyJar jar : MySbtProjectManager.getInstance(context.getProject()).getJars()) {
            context.checkCanceled();  // 与 IDEA 取消机制集成
            VirtualFile vf = VirtualFileManager.getInstance().findFileByNioPath(jar.path());
            if (vf == null) continue;
            SkillCoordinate coord = SkillCoordinate.of(jar.group(), jar.artifact(), jar.version());
            out.add(new SkillJarSource(vf, SkillSourceType.LOCAL_JAR, coord, jar.toString()));
        }
        return out;
    }
}
```

### 5.3 注册扩展点 (skillsjars-integration.xml)

```xml
<idea-plugin>
    <extensions defaultExtensionNs="dev.dong4j.idea.skillsjars.helper">
        <skillSourceScanner implementation="com.example.scanner.MySbtSkillScanner"/>
    </extensions>
</idea-plugin>
```

### 5.4 扫描器硬性规则

| 规则 | 原因 |
|---|---|
| **只产出候选 Jar, 不解析 SKILL.md** | 协调层会调 `SkillJarParser` 统一解析, 你做了就重复 |
| **`isApplicable` 只做内存判断** | 每次刷新都调一次, IO 会拖慢 |
| **长循环加 `context.checkCanceled()`** | 集成 IDEA 进度条 "Stop" 按钮 |
| **不要 `static` 缓存 Jar / VirtualFile** | 扩展点 `dynamic="true"`, 卸载时静态字段会钉住引用导致内存泄漏 |
| **同 Jar 重复返回无害** | 协调层按 jar 路径去重, 第一个出现的扫描器结果获胜 |
| **抛异常会被 catch** | 你的 RuntimeException 不会中断刷新, SkillsJars Helper `LOG.warn` 后跳过. 自己做有意义的错误处理 |

---

## 6. 数据 model 速查

```
SkillJarArtifact ── coordinate ──► SkillCoordinate { groupId, artifactId, version }
                ├── sourceType ──► SkillSourceType (enum)
                ├── jarFile     ──► VirtualFile (IDEA 抽象, 可读 jar)
                ├── libraryName ──► String? (IDEA 库名, 调试用)
                └── skills      ──► List<SkillDescriptor>
                                                ├── name
                                                ├── description?
                                                ├── allowedTools
                                                ├── license?
                                                ├── jarEntryRoot   (jar 内根目录, 以 / 结尾)
                                                ├── skillMdPath    (SKILL.md 完整 jar entry 路径)
                                                ├── body           (去掉 frontmatter 的正文)
                                                └── files          (List<SkillFileEntry>)
```

| Model | 字段语义 |
|---|---|
| `SkillCoordinate` | 三段都可能为 null. 用 `isComplete()` 判断完备. 字符串形式用 `toCoordinateString()` (缺失字段用 `?` 占位) |
| `SkillSourceType` enum | 已发布: `MAVEN_DEPENDENCY / MAVEN_PLUGIN_DEPENDENCY / GRADLE_DEPENDENCY / PROJECT_OUTPUT / EXTERNAL_LIBRARY / LOCAL_JAR`. switch 必须有 default |
| `SkillTargetDirectory` | `agentId` 取自 `PRESET_AGENT_IDS` 之一或 `AGENT_CUSTOM`. `path` 是 nio Path (可能不存在), `execute` 时会创建 |
| `InstallationStatus` enum | 6 个状态互斥, 见 §4.1 |
| `ExportPlan` | 不可变. `withTargetDirectoryName(...)` 复制一份替换目录名 (用于 DUPLICATE_NAME 选 "改后缀") |
| `ExportResult` | `isSuccess()` 区分成功 / 失败; `status` 是回执状态; 失败时 `errorMessage` 给原因 |

---

## 7. Build & Test

```bash
# 你的插件项目里
./gradlew compileJava                     # 编译验证, 必跑
./gradlew runIde                          # 启动沙箱 IDE 同时加载你的插件 + SkillsJars Helper
```

**视觉/行为验收** (在沙箱 IDE 中):
- `SkillRegistry.getInstance(project)` 返回非 null
- 调 `refresh()` 后 listener 能收到回调
- 你的 `SkillSourceScanner.scan()` 被调用 (在实现里加日志确认)
- `SkillExportService.planExport()` 对一个未导出的 skill 返回 `NEW` 状态; execute 后下次返回 `UP_TO_DATE`

**单元测试** — 大部分 model + 接口都不依赖 IDEA Platform, 可以脱离 IDE 容器跑:

```java
// 你的测试代码示例
class MyIntegrationTest {

    @Test
    void should_consume_descriptor_fields() {
        SkillDescriptor skill = new SkillDescriptor(
            "code-review", "审查代码", List.of("Read", "Grep"), "MIT",
            "META-INF/skills/dev/dong4j/code-review/",
            "META-INF/skills/dev/dong4j/code-review/SKILL.md",
            "body content",
            List.of()
        );
        assertThat(skill.getName()).isEqualTo("code-review");
    }
}
```

只在需要 `Project` / `VirtualFile` 真实行为时才用 `BasePlatformTestCase`.

---

## 8. Pitfalls — 真实踩过的坑

### P1. 不要假设 `getArtifacts()` 永远非空

第一次调用前 SkillsJars Helper 可能还没扫过 (用户刚打开 ToolWindow 之前), 返回空列表. 不要 `Objects.requireNonNull` 或者抛异常, 走"暂无数据"的 UI 分支即可.

### P2. 不要在构造器里 `getService(SkillRegistry.class)`

```java
// ❌ 反模式: project 级 service 之间的循环初始化风险
public MyService(Project project) {
    this.registry = SkillRegistry.getInstance(project);  // 不要这样
}

// ✅ 正确: 用 lazy init 或者每次方法调用时取
public void onMyMethodCalled() {
    SkillRegistry registry = SkillRegistry.getInstance(project);
    // ...
}
```

### P3. listener 必须 `Disposer.register`

```java
// ❌ 内存泄漏: 你的组件 dispose 了, listener 还在 SkillsJars Helper 的列表里
registry.addListener(snapshot -> myUi.update(snapshot));

// ✅ 正确: 把订阅句柄注册到 parent disposable
Disposable sub = registry.addListener(snapshot -> myUi.update(snapshot));
Disposer.register(parentDisposable, sub);
```

### P4. `SkillSourceType.values()` switch 必须有 default

```java
// ❌ 危险: SkillsJars Helper 后续可能在末尾追加值
switch (jar.getSourceType()) {
    case MAVEN_DEPENDENCY: ...;
    case GRADLE_DEPENDENCY: ...;
    // 漏了其他几个 + 没有 default
}

// ✅ 正确: 至少有 default 兜底
switch (jar.getSourceType()) {
    case MAVEN_DEPENDENCY -> ...;
    case GRADLE_DEPENDENCY -> ...;
    default -> defaultBranch();
}
```

`InstallationStatus` 同理.

### P5. 不要从 `service/` / `export/` / `parser/` 内部包导入

```java
// ❌ 反模式: 这些不是公共 API, 后续可能改
import dev.dong4j.idea.skillsjars.helper.export.ExportPlanner;
import dev.dong4j.idea.skillsjars.helper.parser.SkillJarParser;

// ✅ 正确: 只从 api/ 包导入
import dev.dong4j.idea.skillsjars.helper.api.SkillRegistry;
import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.model.*;
```

### P6. 扫描器实现里不要静态字段缓存 Project / VirtualFile

`SkillSourceScanner` 扩展点声明 `dynamic="true"`, IDEA 在启用 / 禁用扩展插件时会卸载并重新创建实例. 静态字段缓存 IDE Platform 对象会形成 GC root, 导致 project 关闭后无法释放.

### P7. `SkillTargetDirectory.path` 不保证存在

```java
// ❌ 假设目录存在
File f = target.getPath().toFile();
File[] children = f.listFiles();  // NPE 风险

// ✅ 先判存在
if (Files.isDirectory(target.getPath())) {
    // ...
}
```

`execute` 内部会负责创建; 但你自己读盘时要兜底.

---

## 9. Key Files (在 SkillsJars Helper 源码里)

按重要性递减:

- `src/main/java/.../api/SkillRegistry.java` — 扫描结果查询接口
- `src/main/java/.../api/SkillRegistryListener.java` — 变更监听器
- `src/main/java/.../api/SkillExportService.java` — 导出能力接口
- `src/main/java/.../api/SkillInstallationListener.java` — 安装变更监听器
- `src/main/java/.../api/model/` — 全部公共 model 类
- `src/main/java/.../scanner/SkillSourceScanner.java` — 扫描器扩展点接口
- `src/main/java/.../scanner/SkillJarSource.java` — 扫描器输出类型
- `src/main/java/.../scanner/ScanContext.java` — 扫描上下文
- `src/main/resources/META-INF/plugin.xml` — 扩展点声明 + 服务注册 (供你参考)

完整人类可读文档: [docs/extension-points.md](../../../docs/extension-points.md).

---

## 10. References

- 官方 IntelliJ Plugin Dependencies 文档: <https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html>
- 官方 Extension Points 文档: <https://plugins.jetbrains.com/docs/intellij/plugin-extension-points.html>
- SkillsJars Helper 仓库: <https://github.com/dong4j/skillsjars-helper>
- 上游 SkillsJars 生态: <https://www.skillsjars.com/>
