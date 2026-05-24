---
name: add-project-service
description: 在 SkillsJars Helper 中新增一个项目级 / 应用级 IntelliJ Platform service. 包含官方决策路径 (何时用 plugin.xml 何时用 @Service light service) 与本项目两种 service 的活样板. Use when 用户提到 `projectService` / `applicationService` / `@Service` 注解 / `Service.Level.PROJECT` / 报错 "轻服务必须为具体类" / "Light service must be final" / "@Service 注解的服务类不得在 plugin.xml 中注册" / inspection "服务可被转换为轻服务" / "A service can be converted to a light one", 或者要新增 / 修改一个 IntelliJ Platform 服务.
---

# /add-project-service — 新增 IntelliJ Platform Service

为 SkillsJars Helper 添加一个项目级 (或应用级) service. 本 skill 是项目 service 注册的**单一权威**, 任何 agent 在 `service/` / `api/` 包下新增类时**必须先读完**.

---

## TL;DR — 官方决策路径

根据 IntelliJ Platform 官方文档 [Plugin Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html), 注册方式按两个维度二选一:

```
┌──────────────────────────────────────────────────────────────────────┐
│  这个 service 有独立接口、要对外暴露给三方插件按接口查找吗?              │
├──────────────────────────────────────────────────────────────────────┤
│   是 ──► plugin.xml + serviceInterface + serviceImplementation        │
│         (本项目: SkillRegistry, SkillExportService)                    │
│                                                                       │
│   否 ──► @Service(Service.Level.PROJECT) + final class                │
│         (本项目: InstallationRegistryService)                          │
└──────────────────────────────────────────────────────────────────────┘
```

官方原文（直接引用）:

> "A service **not going to be overridden or exposed as API to other plugins does not need to be registered in plugin.xml**. Instead, annotate the service class with `@Service`."

IDEA 的 inspection `Plugin DevKit | Code | A service can be converted to a light one` 也会主动建议把符合条件的非 light service 转 light service —— 见到这个警告就按规则转, 不要忽略.

## 报错速查表

| 报错 / inspection | 根因 | 修法 |
|---|---|---|
| `轻服务必须为具体类, 不能为抽象类或者为接口` / `Light service must be final` | 在接口或抽象类上标 `@Service` | 接口走 plugin.xml 注册路径, 删 `@Service` |
| `使用 '@Service' 注解的服务类不得在 plugin.xml 中注册` | 实现类同时有 `@Service` + plugin.xml `<projectService>` | 二选一: 走 light service 就只留 `@Service`; 走 API 就只留 plugin.xml |
| `服务可被转换为轻服务` / `A service can be converted to a light one` | 无独立接口的 service 仍走 plugin.xml 注册 | 按本 skill TL;DR 转 light service |
| `Application service assigned to a static final field` | 把 service 实例缓存到 `static final` 字段 | 每次用都 `getInstance()`, 不要缓存 |
| `Mismatch between light service level and its constructor` | Light service 构造器签名与 level 不匹配 | App 级: 无参或 CoroutineScope; Project 级: Project 或 CoroutineScope |

---

## Usage

```
/add-project-service [ServiceName]
```

- `ServiceName` — PascalCase, 例 `FooService`. 决定走哪条路径时, 先回答: **"会有第三方插件按接口调用它吗?"**

---

## 关键规则 (官方原文 + 本项目策略)

规则全部引自 JetBrains 官方文档 [Plugin Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) (GitHub 源: `JetBrains/intellij-sdk-docs/topics/basics/plugin_structure/plugin_services.md`).

### R1. 两种注册方式互斥

| 方式 | 何时用 | 客户端查找 |
|---|---|---|
| **plugin.xml** `<projectService serviceInterface= serviceImplementation=>` | 有独立接口, 对外暴露 API; 或需要 `overrides` / `testServiceImplementation` / `headlessImplementation` | `project.getService(<接口>.class)` (按接口) |
| **`@Service`** light service | 无独立接口, 仅本插件内部使用 | `project.getService(<具体类>.class)` (按实现类) |

**注意**: 同一个类绝对不能两种方式都用 —— IDEA 会报错 "使用 '@Service' 注解的服务类不得在 plugin.xml 中注册". 二选一.

### R2. Light Service (`@Service`) 的硬性限制

- **Service class must be `final`** —— 接口、抽象类、非 final 类**都不行**. 这是 light service 与接口/继承根本不兼容的原因.
- **Constructor injection of dependency services is not supported** —— 构造器**禁止** `getService(...)` 别的 service.
  > 官方原文: "Other service dependencies must be acquired only when needed in all corresponding methods, e.g., if you need a service to get some data or execute a task, retrieve the service before calling its methods. **Do not retrieve services in constructors to store them in class fields.**"
- 不允许这些 plugin.xml 属性: `id`, `os`, `client`, `overrides`, `configurationSchemaKey`, `preload`.
- 应用级 light service 如实现 `PersistentStateComponent`, 必须设 `roamingType = RoamingType.DISABLED`.
- 同一个服务**不能**同时有 `@Service` 注解 + plugin.xml 注册.

对应 inspection (2023.3+):
- `Plugin DevKit | Code | Light service must be final`
- `Plugin DevKit | Code | Mismatch between light service level and its constructor`
- `Plugin DevKit | Code | A service can be converted to a light one`
- `Plugin DevKit | Plugin descriptor | A service can be converted to a light one`

### R3. 构造器约定 (light & 非 light 都适用)

- **App 级**: 构造器可以**无参**, 也可以接受 `CoroutineScope` (Kotlin).
- **Project 级**: 构造器可以接受 `Project` 参数, 也可以接受 `CoroutineScope`.
- **永远不要在构造器里 `getService(...)`** —— 即使没存到字段也算反模式 (会触发循环初始化, 性能差).
- 需要别的 service ? 用 **lazy 初始化**: 在第一次被使用的对外方法里 `getService(...)`, 用 `AtomicBoolean` 守门只做一次. **本项目 `InstallationRegistryService.ensureSubscribed()` 就是范本**.
- Inspection: `Plugin DevKit | Code | Non-default constructors for service and extension class`.

### R4. 取服务的禁忌

> "**Never** acquire service instances prematurely or store them in fields for later use. Instead, **always** obtain service instances directly and **only** at the location where they're needed."

- 禁止 `private static final FooService FOO = ...` 这种字段缓存.
- 服务实现类提供 `static getInstance(Project)` 包装, 是允许且推荐的写法.
- Inspections: `Application service assigned to a static final field` / `Incorrect service retrieving`.

### R5. `serviceInterface` 何时省略

> "If `serviceInterface` isn't specified, it is supposed to have the same value as `serviceImplementation`."

- 有独立接口 → 必须写 `serviceInterface` (省略 → 客户端无法按接口查找).
- 没有独立接口 + 想走 plugin.xml → 省略 `serviceInterface`. 但此时**首选转 light service**, 走 plugin.xml 只有在需要 `os` / `headlessImplementation` 等高级属性时才合理.

---

## Files to Modify — Case A: 有独立接口 (走 plugin.xml)

适用于"想对外暴露 API"的 service. 本项目 `SkillRegistry` / `SkillExportService` 都是这个模式.

### A1. 接口 — `src/main/java/dev/dong4j/idea/skillsjars/helper/api/FooService.java`

```java
package dev.dong4j.idea.skillsjars.helper.api;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 对外暴露的 ... 能力总入口.
 *
 * <p>项目级 service. 在 plugin.xml 中以 {@code serviceInterface=} + {@code serviceImplementation=}
 * 形式注册; 不使用 {@code @Service} 注解, 因为本项目的对外契约要求按接口查找,
 * 而 light service 只支持按具体实现类查找.</p>
 *
 * @author dong4j
 * @since x.y.z
 */
public interface FooService {

    @NotNull
    static FooService getInstance(@NotNull Project project) {
        return project.getService(FooService.class);
    }

    // ... 业务方法 ...
}
```

**禁止**: 给接口加 `@Service` —— IDE 立刻报"轻服务必须为具体类".

### A2. 实现 — `src/main/java/dev/dong4j/idea/skillsjars/helper/service/FooServiceImpl.java`

```java
package dev.dong4j.idea.skillsjars.helper.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import dev.dong4j.idea.skillsjars.helper.api.FooService;

/**
 * {@link FooService} 的项目级实现.
 *
 * @author dong4j
 * @since x.y.z
 */
public final class FooServiceImpl implements FooService, Disposable {

    @NotNull
    private final Project project;

    public FooServiceImpl(@NotNull Project project) {
        this.project = project;
    }

    // ... 实现 ...

    @Override
    public void dispose() {
        // 清理动作
    }
}
```

**禁止**: 给实现类加 `@Service` —— 与 plugin.xml 注册重复, IDE 报"@Service 注解的服务类不得在 plugin.xml 中注册".

### A3. 注册 — `src/main/resources/META-INF/plugin.xml`

```xml
<extensions defaultExtensionNs="com.intellij">
    <!-- 项目级 FooService 服务. 注册 serviceInterface 让外部插件以 API 类查找. -->
    <projectService
        serviceInterface="dev.dong4j.idea.skillsjars.helper.api.FooService"
        serviceImplementation="dev.dong4j.idea.skillsjars.helper.service.FooServiceImpl"/>
</extensions>
```

---

## Files to Modify — Case B: 无独立接口 (走 @Service light service)

适用于"仅本插件内部使用、无 override 需求"的 service. 本项目 `InstallationRegistryService` 是这个模式.

### B1. 实现 — `src/main/java/dev/dong4j/idea/skillsjars/helper/service/FooRegistryService.java`

```java
package dev.dong4j.idea.skillsjars.helper.service;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本插件内部使用的 ... 注册表服务.
 *
 * <p>Light service: 无独立接口, 不对外暴露; 通过 {@code @Service(Service.Level.PROJECT)}
 * 注册, 不在 plugin.xml 出现 (官方推荐路径).</p>
 *
 * @author dong4j
 * @since x.y.z
 */
@Service(Service.Level.PROJECT)
public final class FooRegistryService implements Disposable {

    @NotNull
    private final Project project;

    /** 守门 lazy init: 把"取依赖 service" 等动作延后到首次方法调用时. */
    @NotNull
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Light service 构造器: <strong>只</strong>接受 Project, <strong>不</strong>调用 getService.
     * 重活全部放在 {@link #ensureInitialized()} 里走 lazy 路径.
     */
    public FooRegistryService(@NotNull Project project) {
        this.project = project;
    }

    @NotNull
    public static FooRegistryService getInstance(@NotNull Project project) {
        return project.getService(FooRegistryService.class);
    }

    public void doSomething() {
        this.ensureInitialized();   // ← 所有对外方法第一行
        // ... 业务 ...
    }

    /**
     * 一次性完成对其他 service 的订阅 / 首次扫描等准备工作.
     * Light service 构造器不允许 getService, 因此挪到 lazy init.
     */
    private void ensureInitialized() {
        if (!this.initialized.compareAndSet(false, true)) {
            return;
        }
        // 在这里安全地 getService(...) 别的服务, 并注册 Disposable
        // SomeOtherService other = SomeOtherService.getInstance(project);
        // Disposable sub = other.addListener(...);
        // Disposer.register(this, sub);
    }

    @Override
    public void dispose() {
        // 清理动作
    }
}
```

### B2. `plugin.xml`

**什么都不用加**. Light service 不需要注册. 但建议留一行注释说明这是 light service, 避免后来者找不到这个 service 的注册位置:

```xml
<!-- FooRegistryService 是无独立接口的内部 service, 已通过 @Service(Service.Level.PROJECT)
     声明为 light service, 不在 plugin.xml 注册 (见 IntelliJ Platform 官方推荐). -->
```

---

## Build & Test

```bash
./gradlew compileJava    # 编译验证, 必跑
./gradlew test           # 跑单元测试
./gradlew runIde         # 启动沙箱 IDEA 验证
```

**视觉/行为验收** (在沙箱 IDE 中):

- 在任意位置 `FooService.getInstance(project)` 能拿到非 null 实例.
- 多次调用返回**同一个**对象 (IDEA 内部缓存; 用 `==` 验证).
- 关闭 project 时, 实现的 `dispose()` 被调用 (如果实现了 `Disposable`).
- IDE 的 Inspections (`Analyze | Inspect Code...`) 中, `Plugin DevKit` 分类下无相关警告.

---

## Pitfalls — 真实踩过的坑

### P1. 不要把 `@Service` 标在接口上

历史曾在 `api/SkillExportService` 接口上标 `@Service(Service.Level.PROJECT)`, IDE 立刻报"轻服务必须为具体类". Light service 与接口模式根本不兼容 (`final class` 与 `interface` 互斥).

### P2. 不要同时写 `@Service` 和 plugin.xml

历史曾在实现类标 `@Service` 同时 plugin.xml 也注册, IDE 报"@Service 注解的服务类不得在 plugin.xml 中注册". 两种注册方式互斥, **二选一**.

### P3. 不要在构造器里 `getService` 别的 service

```java
// ❌ 反模式 (light service 直接禁止; 非 light service 也是反模式)
public FooRegistryService(@NotNull Project project) {
    this.project = project;
    this.bar = project.getService(BarService.class);  // 不要这样
}

// ❌ 即使不存字段, 在构造器里 getService 也是反模式
public FooRegistryService(@NotNull Project project) {
    this.project = project;
    project.getService(BarService.class).addListener(this::onChange);  // 仍然不要这样
}

// ✅ 正确做法 —— lazy init 守门
public FooRegistryService(@NotNull Project project) {
    this.project = project;
}

public void onPublicMethodCalled() {
    this.ensureInitialized();   // 第一次调用时才订阅
    // ...
}
```

本项目 `InstallationRegistryService.ensureSubscribed()` 是活样板, 把订阅 + 后台扫描挪到首次方法调用时一次性完成.

### P4. 不要把 service 存进 `static final` 字段

```java
// ❌ Inspection 报 "Application service assigned to a static final field"
private static final FooService FOO = ApplicationManager.getApplication().getService(FooService.class);

// ✅ 每次用都重新取 (IDEA 内部已缓存, 性能成本可忽略)
public void doSomething() {
    FooService foo = FooService.getInstance(project);
    foo.bar();
}
```

### P5. Project 级 service 必须在 Project 打开之后才能取

不要在 `AppLifecycleListener` 等应用启动阶段去 `project.getService(...)`. 至少要等到 `ProjectActivity` (即 `postStartupActivity` 扩展点) 触发后, 或者 ToolWindow / Action 被用户主动调用时.

### P6. 别把"内部 service"硬塞进 plugin.xml 注册

> 看到 IDE 报 `服务可被转换为轻服务` / `A service can be converted to a light one` —— 这正是 IDEA 在主动告诉你"你这个 service 没有独立接口、没有 override 需求, 应该走 `@Service` 路径"。**不要忽略这条 inspection**.

本项目曾经一度把所有 service (包括无独立接口的 `InstallationRegistryService`) 都走 plugin.xml "为了风格统一", 结果 IDE 报这个警告. 正解是**接受官方决策路径**: 有接口走 plugin.xml, 没接口走 `@Service`.

---

## Canonical Example

本项目现有三个项目级 service, 完整覆盖两种路径, 是本 skill 的活样板:

| Service | 是否有独立接口 | 注册方式 | 客户端查找 |
|---|---|---|---|
| `SkillRegistry` | ✅ 有 (`api/SkillRegistry`) | plugin.xml + `serviceInterface` + `serviceImplementation` | `project.getService(SkillRegistry.class)` |
| `SkillExportService` | ✅ 有 (`api/SkillExportService`) | plugin.xml + `serviceInterface` + `serviceImplementation` | `project.getService(SkillExportService.class)` |
| `InstallationRegistryService` | ❌ 无 | `@Service(Service.Level.PROJECT)` light service | `project.getService(InstallationRegistryService.class)` |

参考阅读:
- `src/main/resources/META-INF/plugin.xml` 的 `<extensions>` 段（Case A 的两份注册）
- `src/main/java/dev/dong4j/idea/skillsjars/helper/service/InstallationRegistryService.java`（Case B 的完整范本, 含 lazy init 守门写法）

---

## Key Files

- `src/main/resources/META-INF/plugin.xml` — Case A 注册的单一权威源
- `src/main/java/dev/dong4j/idea/skillsjars/helper/api/` — 对外服务**接口**所在包 (Case A, 公共契约)
- `src/main/java/dev/dong4j/idea/skillsjars/helper/service/` — 服务**实现**所在包 (Case A 实现 + Case B 完整类)
- [`AGENTS.md`](../../../AGENTS.md) §5「关键不变量」 §6「公共 API」 — 项目对 `api/` 包的契约要求

## References — 官方文档

- [Plugin Services](https://plugins.jetbrains.com/docs/intellij/plugin-services.html) — 本 skill 的全部规则来源
- [Light Services 一节](https://plugins.jetbrains.com/docs/intellij/plugin-services.html#light-services) — `@Service` 的限制清单
- [Retrieving a Service 一节](https://plugins.jetbrains.com/docs/intellij/plugin-services.html#retrieving-a-service) — `getService` 的禁忌
- 官方源 markdown: <https://github.com/JetBrains/intellij-sdk-docs/blob/main/topics/basics/plugin_structure/plugin_services.md>
