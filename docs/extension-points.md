# SkillsJars Helper 扩展点接入文档

> 面向**第三方 IDEA 插件开发者** —— 如果你要在自己的插件里复用 SkillsJars Helper 的扫描结果, 或者把自定义构建系统接入 SkillsJars Helper
> 的扫描流水线, 这是你需要读的唯一文档.

---

## 你能从 SkillsJars Helper 拿到什么

| 能力                             | 形态                               | 用途                                                                  |
|--------------------------------|----------------------------------|---------------------------------------------------------------------|
| **当前项目里所有 `SKILL.md` Jar 的索引** | `SkillRegistry` 项目级 service      | 在你的插件 UI / 推荐器 / 上下文注入器中消费已扫描出来的 Skill, 不需要自己解析 Jar                 |
| **Skill 导出到 Agent 目录的能力**      | `SkillExportService` 项目级 service | 在你的插件里一键把 Skill 落地到 `.claude/skills` / `.cursor/skills` 等目录         |
| **扫描结果变更事件**                   | `SkillRegistryListener`          | 索引刷新时自动收到回调 (UI 跟随刷新 / 缓存失效)                                        |
| **安装状态变更事件**                   | `SkillInstallationListener`      | 导出 / 删除 / 重算后自动收到回调                                                 |
| **接入新的 Skill 来源**              | `skillSourceScanner` 扩展点         | 当你的项目用了非 Maven/Gradle 的自研构建工具时, 实现一个扫描器把 Jar 路径喂给 SkillsJars Helper |

**对外契约稳定性**: `api/` 包下的所有接口与 model 都是公共 API. 不会做破坏性变更; 任何破坏二进制兼容的改动都会升 minor 版本并在
`pluginChanges.html` 写明迁移说明.

---

## 1. 让你的插件依赖 SkillsJars Helper

`build.gradle.kts`:

```kotlin
intellijPlatform {
    // 已加载 SkillsJars Helper 时才注册扩展点 / 调用 API
    plugins("dev.dong4j.idea.skillsjars.helper:<version>")
}
```

`plugin.xml`:

```xml
<!-- optional=true 让你的插件在用户没装 SkillsJars Helper 时仍能加载,
     只是相关功能会被跳过. config-file 里放只在 SkillsJars Helper 存在时才需要的扩展点 / 监听器. -->
<depends optional="true" config-file="skillsjars-integration.xml">
    dev.dong4j.idea.skillsjars.helper
</depends>
```

`skillsjars-integration.xml` (按需创建):

```xml
<idea-plugin>
    <extensions defaultExtensionNs="dev.dong4j.idea.skillsjars.helper">
        <!-- 注册自定义来源扫描器, 见下文 §4 -->
        <skillSourceScanner implementation="com.example.MyGradleSkillScanner"/>
    </extensions>
</idea-plugin>
```

> **小提示**: 如果你的插件**只**调用 `SkillRegistry` / `SkillExportService` (不实现扩展点), 不需要 `config-file`, 直接
`<depends optional="true">` 即可. config-file 的作用是把 "依赖宿主存在时才注册的扩展点" 隔离, 避免宿主缺席时类加载失败.

---

## 2. 查询当前项目里的所有 Skill

```java
import dev.dong4j.idea.skillsjars.helper.api.SkillRegistry;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillDescriptor;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillJarArtifact;

public void useSkillsInMyPlugin(Project project) {
    SkillRegistry registry = SkillRegistry.getInstance(project);

    // 同步读取当前快照 (只读, 不触发扫描)
    for (SkillJarArtifact jar : registry.getArtifacts()) {
        System.out.println(jar.getCoordinate());       // groupId:artifactId:version
        for (SkillDescriptor skill : jar.getSkills()) {
            System.out.println(" - " + skill.getName());
            System.out.println("   description: " + skill.getDescription());
            System.out.println("   allowed-tools: " + skill.getAllowedTools());
        }
    }

    // 按名称 / 坐标查找
    List<SkillDescriptor> codeReviewSkills = registry.findByName("code-review");
    List<SkillJarArtifact> zekaJars = registry.findByCoordinate("dev.dong4j:zeka-skills");
}
```

**线程模型**:

- `getArtifacts()` / `getSkills()` / `findBy*()` 都是同步读取内存快照, 可以在任意线程调用, 无锁.
- 第一次调用前如果 SkillsJars Helper 还没扫过, 返回**空列表**. 这是正常的, 你可以选择主动 `refresh()` 或订阅监听器等待刷新.

---

## 3. 订阅扫描结果变化

```java
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;

public void subscribe(Project project, Disposable parentDisposable) {
    SkillRegistry registry = SkillRegistry.getInstance(project);

    Disposable subscription = registry.addListener(snapshot -> {
        // 在 EDT 上回调; 重活请自行切后台
        myUi.update(snapshot);
    });

    // 把订阅与你的组件生命周期绑定, 避免泄漏
    Disposer.register(parentDisposable, subscription);
}
```

**事件保证**:

- 回调**在 EDT 上**派发 (与 IDEA 通用约定一致), 你可以直接 `setText` / `repaint` Swing 组件.
- 监听器之间相互隔离: 你的监听器抛出异常会被 SkillsJars Helper 捕获并记 `LOG.warn`, 不会影响其他监听者.
- 不保证顺序性, 不保证去重; 同一份快照可能因 IDE 触发多次 refresh 被派发多次.

---

## 4. 接入自定义 Skill 来源 (扫描器扩展点)

SkillsJars Helper 一期内置了两种来源扫描器:

- `MavenLibraryScanner` —— 识别 `<dependencies>` 中的 Maven Jar
- `MavenPluginDependencyScanner` —— 识别 `skillsjars-maven-plugin` 的 `<dependencies>` 块

如果你的项目用了别的构建工具 (Gradle / SBT / 企业自研 IDE 工具集), 实现 `SkillSourceScanner` 即可接入.

### 4.1 实现一个扫描器

```java
package com.example.gradle;

import org.jetbrains.annotations.NotNull;
import java.util.List;

import dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner;
import dev.dong4j.idea.skillsjars.helper.scanner.ScanContext;
import dev.dong4j.idea.skillsjars.helper.scanner.SkillJarSource;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillCoordinate;
import dev.dong4j.idea.skillsjars.helper.api.model.SkillSourceType;

/**
 * 把 Gradle 项目的依赖暴露给 SkillsJars Helper.
 *
 * <p>扫描器只负责"找到候选 Jar 并打上来源标签", 解析 SKILL.md 由 SkillsJars Helper
 * 协调层 + parser 负责, 你不需要操心.</p>
 */
public final class MyGradleSkillScanner implements SkillSourceScanner {

    @NotNull
    @Override
    public String getDisplayName() {
        return "Gradle Dependencies";
    }

    @Override
    public boolean isApplicable(@NotNull ScanContext context) {
        // 只在 Gradle 项目里运行, 避免在 Maven-only 项目中做无谓 IO
        return GradleSettings.getInstance(context.getProject()).getLinkedProjectsSettings().size() > 0;
    }

    @NotNull
    @Override
    public List<SkillJarSource> scan(@NotNull ScanContext context) {
        List<SkillJarSource> out = new ArrayList<>();
        for (Module module : ModuleManager.getInstance(context.getProject()).getModules()) {
            context.checkCanceled();  // 与 IDEA 的取消机制集成
            OrderEnumerator.orderEntries(module).librariesOnly()
                .forEachLibrary(library -> {
                    String name = library.getName();
                    if (name == null || !name.startsWith("Gradle: ")) {
                        return true;
                    }
                    SkillCoordinate coord = SkillCoordinate.fromLibraryName(name);
                    for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
                        VirtualFile jarFile = toLocalJarFile(root);
                        if (jarFile != null) {
                            out.add(new SkillJarSource(jarFile, SkillSourceType.GRADLE_DEPENDENCY, coord, name));
                        }
                    }
                    return true;
                });
        }
        return out;
    }
}
```

### 4.2 注册扩展点

`plugin.xml` (你的插件) 的 config-file 里:

```xml
<extensions defaultExtensionNs="dev.dong4j.idea.skillsjars.helper">
    <skillSourceScanner implementation="com.example.gradle.MyGradleSkillScanner"/>
</extensions>
```

### 4.3 扫描器编写要点

| 要点                          | 说明                                                                                                    |
|-----------------------------|-------------------------------------------------------------------------------------------------------|
| **只返回候选 Jar**               | 不要在扫描器里打开 Jar 解析 SKILL.md, SkillsJars Helper 协调层会统一调用 `SkillJarParser`, 也会做去重                         |
| **`isApplicable` 要快**       | 这个方法每次刷新都会被调用; 不要做 IO. 用 `Settings` / `ModuleManager` 之类的内存判断                                         |
| **支持取消**                    | 长循环里调用 `context.checkCanceled()`. 这是与 IDEA 进度条 "Stop" 按钮的集成                                           |
| **`SkillSourceType` 是公共枚举** | 已发布的枚举值不会被删, 新增项会放在末尾; 你扫描到的 Jar 用哪一项, 由扫描器自行决定, 后续如果有更合适的来源类型 (例如 `GRADLE_DEPENDENCY`) 已经在枚举里就请用最贴切的 |
| **同 Jar 重复无害**              | 多个扫描器扫到同一个 Jar 是允许的, 协调层会按 jar 路径去重, 保留第一个扫描器的结果. 后续可能加优先级排序                                          |
| **不要持有静态字段**                | 扩展点声明为 `dynamic="true"`, IDEA 在用户启用 / 禁用扩展插件时会卸载 + 重建. 用 `static` 缓存会被 GC 钉住, 形成内存泄漏                  |
| **抛异常会被吞**                  | 实现里 `throw RuntimeException` 不会中断整个刷新流程, SkillsJars Helper 会 `LOG.warn` 后跳过该扫描器. 你应该自行做有意义的错误处理       |

---

## 5. 自动导出 Skill 到 Agent 目录

```java
import dev.dong4j.idea.skillsjars.helper.api.SkillExportService;
import dev.dong4j.idea.skillsjars.helper.api.model.*;

public void exportToClaude(Project project, SkillJarArtifact jar, SkillDescriptor skill) {
    SkillExportService export = SkillExportService.getInstance(project);

    // 1. 拿到当前项目所有预设 Agent 目录候选
    List<SkillTargetDirectory> targets = export.detectTargets(project);
    SkillTargetDirectory claudeDir = targets.stream()
        .filter(t -> SkillTargetDirectory.AGENT_CLAUDE.equals(t.getAgentId()))
        .findFirst().orElseThrow();

    // 2. 计算导出计划 (不会写盘, 只做哈希比对 + 状态判定)
    ExportPlan plan = export.planExport(jar, skill, claudeDir);

    // 3. 根据 plan.status 决定 UI 行为
    switch (plan.getStatus()) {
        case NEW, OUTDATED         -> doExport(export, plan);      // 直接覆盖
        case UP_TO_DATE            -> notifyUser("已是最新, 跳过");
        case LOCALLY_MODIFIED,
             FOREIGN               -> if (confirm("即将覆盖本地修改")) doExport(export, plan);
        case DUPLICATE_NAME        -> handleConflict(export, plan); // 同名冲突: 覆盖 / 改后缀 / 取消
    }
}

private static void doExport(SkillExportService export, ExportPlan plan) {
    ExportResult result = export.execute(plan);
    if (!result.isSuccess()) {
        Messages.showErrorDialog("导出失败: " + result.getErrorMessage(), "Export");
    }
}
```

**关键不变量**:

- `planExport` 是**只读**的, 不会触碰磁盘以外的状态. 多次调用同一参数返回的 `ExportPlan` 在数据层面等价.
- `execute` 是**幂等**的, 重复执行同一 plan 不会留下半成品 (内部走临时目录 + atomic move).
- `InstallationStatus` 是公共枚举, 6 个值 (`NEW / UP_TO_DATE / OUTDATED / LOCALLY_MODIFIED / FOREIGN / DUPLICATE_NAME`) 永远互斥.
  不要假设新增项会放在某个位置.

---

## 6. 订阅安装状态变化

```java
import com.intellij.openapi.Disposable;

public void subscribeInstallationChanges(Project project, Disposable parent) {
    SkillExportService export = SkillExportService.getInstance(project);

    Disposable subscription = export.addInstallationListener(src -> {
        // 任何 skill 被导出 / 卸载 / 重算后回调, 在 EDT 上触发
        myInstalledBadgeUi.refresh();
    });

    Disposer.register(parent, subscription);
}
```

**事件粒度**:

- 故意保持粗粒度: 不区分单 skill 还是批量, 不携带 diff. 监听者应该重新查询当前需要展示的状态.
- 这种设计避免了 "事件风暴" + "增量维护噩梦", 与 IDEA 内部很多事件 (例如 `VirtualFileManager.VFS_CHANGES`) 的语义一致.

---

## 7. 数据模型字段参考

### `SkillJarArtifact`

| 字段            | 类型                      | 说明                                                       |
|---------------|-------------------------|----------------------------------------------------------|
| `jarFile`     | `VirtualFile`           | Jar 本身的虚拟文件 (不是 jar 内目录)                                 |
| `sourceType`  | `SkillSourceType`       | 来源标签 (Maven / Maven Plugin / Gradle / Local Jar / ...)   |
| `coordinate`  | `SkillCoordinate`       | `groupId:artifactId:version` 三段坐标; 解析不出时是 `unknown()` 占位 |
| `skills`      | `List<SkillDescriptor>` | 该 Jar 内识别到的所有 Skill (至少 1 个)                             |
| `libraryName` | `String?`               | IDEA 库名 (例如 `"Maven: g:a:v"`), 用于调试                      |

### `SkillDescriptor`

| 字段             | 类型                     | 说明                                                                         |
|----------------|------------------------|----------------------------------------------------------------------------|
| `name`         | `String`               | 优先取 frontmatter 的 `name`, 没有时退化为 Jar 内根目录末段                                |
| `description`  | `String?`              | frontmatter 中的 `description`                                               |
| `allowedTools` | `List<String>`         | `allowed-tools` 拆分结果, 没有时是空列表                                              |
| `license`      | `String?`              | frontmatter 中的 `license`                                                   |
| `jarEntryRoot` | `String`               | Skill 在 Jar 内的根目录 (以 `/` 结尾), 例如 `META-INF/skills/dev/dong4j/code-review/` |
| `skillMdPath`  | `String`               | `SKILL.md` 在 Jar 内的完整路径                                                    |
| `body`         | `String`               | SKILL.md 去掉 frontmatter 后的正文                                               |
| `files`        | `List<SkillFileEntry>` | Skill 根目录下所有文件清单 (相对路径 + 大小)                                               |

### `SkillTargetDirectory`

| 字段            | 类型       | 说明                                              |
|---------------|----------|-------------------------------------------------|
| `agentId`     | `String` | 稳定 ID, 取自 `PRESET_AGENT_IDS` 之一或 `AGENT_CUSTOM` |
| `path`        | `Path`   | 磁盘绝对路径; 注意目录**可能尚未存在**, `execute` 时会创建          |
| `displayName` | `String` | 仅供 UI 展示, 不参与判等                                 |

`PRESET_AGENT_IDS` 当前包含 9 个 Agent: `agents / claude / codebuddy / codex / cursor / gemini / junie / qoder / trae`. 列表会在新版本里追加,
不会删项.

---

## 8. 兼容性承诺

| 变更类型                                                   | 处理方式                                                     |
|--------------------------------------------------------|----------------------------------------------------------|
| 在 `api/` 包接口里**新增方法** (带默认实现或仅在新版本调用)                  | 视为兼容变更, patch 版本即可                                       |
| 在 `SkillSourceType` / `InstallationStatus` 枚举**末尾追加值** | 视为兼容变更; 你的 switch 应该有 `default` 分支兜底                     |
| 在 model 类**新增字段** + getter                             | 视为兼容变更                                                   |
| 删除接口方法 / 修改签名 / 改字段含义                                  | 视为**破坏性变更**, 必须升 minor 版本 + 在 `pluginChanges.html` 写迁移说明 |
| 删除已发布的枚举值                                              | 视为**破坏性变更**, 走 minor 升级路径                                |

实现自定义扩展点的插件应该:

- **不要**假设 `getSkills()` 返回的是某种特定 `List` 实现 (例如 `ArrayList`), 我们只承诺 `List`.
- **不要**假设字段非 null 是因为某个值"以前从不为 null"; 检查 `@Nullable` 标注是唯一可信信号.
- **不要**反射访问 `service/` / `export/` / `parser/` / `scanner/` 内部包, 那不是公共 API.

---

## 9. 调试技巧

| 现象                               | 排查                                                                                                                        |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `registry.getArtifacts()` 长期返回空  | 看 IDEA Log: `Help → Show Log in Finder/Explorer`, 搜 `SkillsJars` 看是否有 `Scanner failed`                                    |
| 自己的 `SkillSourceScanner` 没被调用    | 在 `isApplicable` 里加日志确认 IDE 是否能找到你的扩展; 确认 `<depends optional="true">` 没把你的整个 `config-file` 误关                             |
| 自定义扩展点抛 `ClassNotFoundException` | 检查 `dynamic="true"` 兼容, 不要在 `static` 字段缓存 IDEA Platform 类                                                                 |
| `addListener` 回调不来               | 确认你订阅的是**这个项目**的 service (`registry.getInstance(project)`), 别拿别的 project 的实例                                              |
| 导出后 IDE 项目视图不刷新                  | SkillsJars Helper 自己会做 VFS refresh, 但如果你直接调 `execute` 后立刻读磁盘内容, 注意要重新走 `LocalFileSystem.refreshAndFindFileByNioFile(...)` |

---

## 10. 关键引用

- 扫描结果接口: [`SkillRegistry`](../src/main/java/dev/dong4j/idea/skillsjars/helper/api/SkillRegistry.java)
- 导出能力接口: [`SkillExportService`](../src/main/java/dev/dong4j/idea/skillsjars/helper/api/SkillExportService.java)
- 扫描器扩展点: [`SkillSourceScanner`](../src/main/java/dev/dong4j/idea/skillsjars/helper/scanner/SkillSourceScanner.java)
- 数据 model: [`api/model/`](../src/main/java/dev/dong4j/idea/skillsjars/helper/api/model/)
- AI 友好的快速接入入口: [`integrate-skillsjars-helper` skill](../.claude/skills/integrate-skillsjars-helper/SKILL.md)
- 内部架构 (仅当你需要修改 SkillsJars Helper 本身时阅读): [`AGENTS.md`](../AGENTS.md), [`docs/design.md`](design.md)

如有问题 / 建议, 请在 [GitHub 仓库](https://github.com/dong4j/skillsjars-helper) 提 Issue 或 Discussion.
