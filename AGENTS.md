# AGENTS.md — SkillsJars Helper

> 面向多 Agent 协作开发的项目地图与协作契约。
> 任何 Coding Agent 在本仓库内动手之前，先读完本文件再决定动哪一层。

---

## 1. TL;DR

- **是什么**：JetBrains IDE 插件，把以 Maven artifact 形式分发的 Agent Skills（SkillsJars）变成 IDE 一等公民 —— 不解压 JAR 即可发现 / 预览 / 导出
  `SKILL.md`。
- **技术栈**：Java 21 · IntelliJ Platform Gradle Plugin 2.16.0 · IDEA Community/Ultimate 2024.2 → 2025.3 · Lombok · JUnit 5 + Mockito +
  AssertJ。
- **当前阶段**：一期（扫描 + ToolWindow）、二期（导出 + manifest + 安装状态）**已完成并发布**；三期（发布前检查、Gradle 扫描器、`allowed-tools` 风险检查）
  **规划中**。
- **架构基线**：分层清晰 —— `scanner` 找 JAR → `parser` 解析 SKILL.md → `service` 暴露 API（`SkillRegistry` / `SkillExportService` /
  `InstallationRegistryService`） → `toolwindow` 渲染 → `export` 写盘。一切对外能力都通过 `api/` 包暴露，**禁止跨层反向依赖**。

---

## 2. 一分钟跑起来

```bash
./gradlew runIde           # 启动内嵌 IDE 验证插件
./gradlew test             # 跑单元测试 (JUnit 5)
./gradlew buildPlugin      # 构建可分发的 zip
./gradlew verifyPlugin     # 跑 IntelliJ Platform 兼容性校验
./gradlew publishBeta      # 发布到 Marketplace beta 通道 (需环境变量)
./gradlew publishDefault   # 发布到 default 通道 (隐藏发布, 需环境变量)

./deploy.sh -h             # 看部署脚本完整 usage
./deploy.sh -d             # 仅 rsync landing/ 到自有服务器 (最常用)
./deploy.sh                # publishDefault + 上传 zip + 部署 landing 三件套
```

> `runIde` 已开启 `-XX:+AllowEnhancedClassRedefinition`，支持简单方法体级热替换。
> 发布相关任务需要 `CERTIFICATE_CHAIN` / `PRIVATE_KEY` / `PRIVATE_KEY_PASSWORD` / `PUBLISH_TOKEN` 环境变量，本地通常不用关心。
> `deploy.sh` 顶部 CONFIG 区有 `REMOTE_HOST` / `REMOTE_ROOT_DIR` / `SITE_URL` 三个必改项, 也可用 `SKILLSJARS_DEPLOY_*` 环境变量临时覆盖, 详见脚本头部注释或 `landing/README.md`.

---

## 3. 目录地图

```
skillsjars-helper/
├── build.gradle.kts                     IntelliJ Platform Gradle Plugin 配置 + 发布通道路由
├── gradle.properties                    pluginVersion / 兼容平台范围 / Java 版本
├── includes/                            插件市场页内容 (HTML), 通过 pluginConfiguration 注入
│   ├── pluginDescription.html
│   └── pluginChanges.html
├── deploy.sh                            一键部署脚本 (publish + 上传 zip + rsync landing)
├── docs/
│   ├── design.md                        一/二期完整设计 (架构事实来源)
│   ├── extension-points.md              第三方插件接入扩展点的官方说明 (公共 API + skillSourceScanner)
│   └── phase3-publish.md                三期发布功能需求草案
├── landing/                             对外 landing page (静态 HTML, 中英双语)
│   ├── index.html                       英文落地页
│   ├── zh/index.html                    中文落地页
│   ├── assets/                          双语共享 (styles / main.js / 图标 / banner)
│   ├── nginx.conf                       站点 nginx 配置 (deploy.sh -n 上传)
│   └── README.md                        landing 自身的 README (本地预览 / 部署细节)
├── src/main/
│   ├── java/dev/dong4j/idea/skillsjars/helper/
│   │   ├── api/                         公共 API (对外稳定契约)
│   │   ├── scanner/                     SkillSourceScanner 扩展点 + Maven 实现
│   │   ├── parser/                      JAR / SKILL.md frontmatter 解析
│   │   ├── service/                     IntelliJ 项目级 Service (注册在 plugin.xml)
│   │   ├── export/                      导出规划 / 执行 / Manifest / Hash
│   │   ├── toolwindow/                  Agent Skills Tool Window 与交互
│   │   ├── util/                        通用工具 (Notification / Bundle)
│   │   └── PluginContents.java          插件级常量
│   └── resources/
│       ├── META-INF/plugin.xml          主 plugin.xml
│       ├── META-INF/skillsjars-maven.xml  可选 Maven 子模块 (depends optional)
│       ├── messages/                    SkillsJarsHelperBundle (en + zh_CN)
│       └── icons/agents/                9 个 Agent 品牌徽标 (1x + @2x)
└── src/test/                            JUnit 5 单元测试
```

---

## 4. 包职责 / 修改入口表（Agent 第一站）

| 你的任务                                             | 看这里                                                                                                               | 备注                                                                                                               |
|--------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| 加一种新的 skill 来源扫描器（Gradle / SBT / IDEA Library …） | `scanner/SkillSourceScanner.java` + 新实现类，注册到 `plugin.xml` 的 `<skillSourceScanner>`                                | **不要**修改 `service/SkillRegistryService` —— 它只编排，不知道具体来源                                                          |
| 改 SKILL.md 解析规则、frontmatter 字段                   | `parser/SkillFrontmatterParser.java` + `parser/ParsedSkillMd.java`                                                | 新字段先加到 `api/model/SkillDescriptor` 才能被 ToolWindow / 导出层看见                                                        |
| 改导出策略 / 冲突机 / Manifest 结构                        | `export/ExportPlanner.java`（决策）+ `export/ExportExecutor.java`（落盘）+ `export/Manifest*`                             | 6 状态（NEW / UP_TO_DATE / OUTDATED / LOCALLY_MODIFIED / FOREIGN / DUPLICATE_NAME）的决策**只在 ExportPlanner**，执行层不要再做判断 |
| 加新的 Agent 目标目录（第 10 个 agent）                     | `api/model/SkillTargetDirectory`（枚举）+ `icons/SkillsJarsHelperIcons` + `resources/icons/agents/*.png` + 国际化键       | 必须同时提供 1x 和 @2x 图标；ResourceBundle 中英两份都要补                                                                        |
| 改 ToolWindow 树 / 渲染 / 右键菜单                       | `toolwindow/SkillsToolWindowPanel.java` + `SkillsTreeModel` + `SkillsTreeCellRenderer` + `SkillExportInteraction` | 渲染层禁止直接读 JAR / 写盘 —— 一律走 `SkillRegistry` / `SkillExportService`                                                  |
| 暴露给第三方插件的能力                                      | `api/` 包（Listener / Service 接口 / model）                                                                           | 这是**公共契约**，破坏性改动需要在 `pluginChanges.html` 显式注明；对外接入指南见 [`docs/extension-points.md`](docs/extension-points.md) 与 [`.claude/skills/integrate-skillsjars-helper`](.claude/skills/integrate-skillsjars-helper/SKILL.md) |
| 新增 IntelliJ Platform service（projectService / applicationService） | 先读 [`.claude/skills/add-project-service`](.claude/skills/add-project-service/SKILL.md)                            | **官方决策路径**：有独立接口、对外暴露 → `plugin.xml` + `serviceInterface` + `serviceImplementation`；无接口、内部使用 → `@Service(Service.Level.PROJECT)` light service。详见 §8 第 7 条 |
| 国际化文案                                            | `resources/messages/SkillsJarsHelperBundle*.properties` + `util/SkillsJarsHelperBundle.java`                      | 中英两份必须同时增删，键名同步                                                                                                  |
| 通知 / Balloon                                     | `util/NotificationUtil.java` + `plugin.xml` 中 `notificationGroup="SkillsJars Helper Notifications"`               | 不要在业务层 `new Notification(...)`                                                                                   |
| 修市场页文案 / 链接                                      | `includes/pluginDescription.html` + `pluginChanges.html`                                                          | 见 §8「已知陷阱」中的实体渲染问题                                                                                               |
| 修兼容平台范围 / Java 版本                                | `gradle.properties` + `build.gradle.kts` 中 `pluginVerification.ides`                                              | sinceBuild 调整后必须本地跑 `verifyPlugin`                                                                               |

---

## 5. 架构与数据流（必读一次）

```
  ┌──────────────────────────────────────────────────────────────┐
  │  ScanContext  ──►  SkillSourceScanner (ext-point)            │   插件加载 / 手动 Refresh
  │       (Maven / MavenPlugin / 未来 Gradle …)                  │       触发整条链路
  │                       │                                      │
  │                       ▼                                      │
  │              SkillJarSource (JAR URL + coordinate)           │
  │                       │                                      │
  │                       ▼                                      │
  │          SkillJarParser ──► SkillFrontmatterParser           │
  │                       │                                      │
  │                       ▼                                      │
  │             SkillDescriptor / SkillJarArtifact (api/model)   │
  │                       │                                      │
  │             ┌─────────┴─────────┐                            │
  │             ▼                   ▼                            │
  │  SkillRegistryService   SkillsToolWindowPanel                │
  │  (项目级 Service)        (UI, 树 + 描述面板)                 │
  │             │                   │                            │
  │             │      右键 ▼        │                            │
  │             │   SkillExportInteraction                       │
  │             │             │                                  │
  │             │             ▼                                  │
  │  SkillExportServiceImpl ──► ExportPlanner ──► ExportExecutor │
  │             │                   │                   │        │
  │             │                   │                   ▼        │
  │             │                   │            写盘 + ManifestJson
  │             │                   │                   │        │
  │             ▼                   ▼                   ▼        │
  │  InstallationRegistryService ◄────── SkillInstallationListener
  │  (扫描 9 个预设目录的 manifest, 提供徽标数据)                 │
  └──────────────────────────────────────────────────────────────┘
```

**关键不变量（Invariants）**

- `api/` 包内的接口、model 是**对外契约**，破坏性变更必须升 minor 版本 + 在 `pluginChanges.html` 写明迁移说明。
- 任何"读 JAR"动作都走 `SkillJarParser`，不允许 UI / Service 自己 `new ZipInputStream`。
- 任何"读 / 写已导出目录"动作都走 `InstallationRegistryService` + `ExportExecutor`，不允许 UI 自己 `Files.write`。
- 导出冲突的"决策"只能在 `ExportPlanner` 里（产出 `ExportPlan`），`ExportExecutor` 只忠实执行。
- ToolWindow 渲染层只能消费 `SkillRegistry` / `InstallationRegistryService` 的快照，不允许反向调用 scanner / parser。

---

## 6. 公共 API（对三方插件）

在 `plugin.xml` 中以 `projectService` 形式注册，`serviceInterface` 即对外契约：

| 接口                              | 用途                  | 关键方法                                                                                |
|---------------------------------|---------------------|-------------------------------------------------------------------------------------|
| `api.SkillRegistry`             | 查询当前项目的 skill 快照    | `getArtifacts()` / `findBy*()` / `refresh()` / `addListener(SkillRegistryListener)` |
| `api.SkillExportService`        | 导出 skill 到 Agent 目录 | `planExport(...)` / `execute(ExportPlan)`                                           |
| `api.SkillInstallationListener` | 导出结果监听              | `onInstalled(...)` / `onUpdated(...)` / `onSkipped(...)`                            |

扩展点（供三方插件实现）：

- `dev.dong4j.idea.skillsjars.helper.skillSourceScanner` —— 实现 `SkillSourceScanner` 即可接入自定义构建系统的 skill 来源扫描。
  `dynamic="true"`，**禁止**做静态字段缓存。

> Agent 在新增 / 修改任何 `api/` 下文件时，**必须**同步考虑两件事：(a) 是否破坏二进制兼容；(b) 是否需要在 `pluginChanges.html` 写一行变更说明。

---

## 7. 编码约定

- **Java 21**：可以用 `record` / `sealed` / pattern matching。新数据模型优先用 `record`。
- **Lombok**：允许 `@Getter / @Builder / @RequiredArgsConstructor / @Slf4j`；禁止 `@Data`（避免 `equals/hashCode` 不可控）。
- **空值**：API 返回 `Optional<T>` 或不可变集合，不返回 `null`。集合空状态用 `Collections.emptyList()`。
- **注释（按用户规则）**：
    - 新增完整文件必须有模块级注释（顶部）说明**为什么**有这个文件、它的角色。
    - 关键类、关键方法补 Javadoc，重点写 **why / 关键约束**，不要重复 `what`。
    - 不允许只提交裸代码。但也不要写废话注释（`// 设置 name`）。
- **包内可见性**：scanner / parser / export 内部协作类用 package-private，不要随手 `public`。只有 `api/` 包的成员是默认对外的。
- **IntelliJ Platform 调用**：访问 PSI / VirtualFile / 文件系统时尊重读写锁；后台任务用 `Task.Backgroundable`，UI 更新走
  `ApplicationManager.getApplication().invokeLater()`。
- **测试**：单元测试可以脱离 Platform 跑（看 `ExportPlannerTest` / `SkillFrontmatterParserTest` 的写法）；需要 Platform 上下文的测试用
  `testFramework(TestFrameworkType.Platform)` 提供的 `BasePlatformTestCase`。
- **国际化**：所有面向用户的字符串走 `SkillsJarsHelperBundle.message("...")`；新增 key 必须同时写 `*.properties` 与 `*_zh_CN.properties`。

---

## 8. 已知陷阱（**踩过坑，不要再踩**）

1. **Maven 依赖是可选的**
    - `plugin.xml` 用 `<depends optional="true" config-file="skillsjars-maven.xml">org.jetbrains.idea.maven</depends>`。
      `MavenPluginDependencyScanner` 注册在 `skillsjars-maven.xml`，**禁止**把它移到主 `plugin.xml`，否则没装 Maven 插件的 IDE 会启动失败。
2. **`InstallationRegistryService` 状态来自磁盘扫描，不是事件累计**
    - 启动时 + 每次导出后会重扫 9 个预设目录的 `.skillsjars-helper.json`。改导出逻辑后必须验证：删除磁盘上的 manifest 应能让徽标消失；手工修改
      SKILL.md 应能让状态变 `LOCALLY_MODIFIED`。
3. **`InstallationStatus` 6 个枚举值是契约**
    - `NEW / UP_TO_DATE / OUTDATED / LOCALLY_MODIFIED / FOREIGN / DUPLICATE_NAME` 与用户交互（静默 / yes-no / 三选项）一一对应。新增状态必须同时改
      `ExportPlanner` + `SkillExportInteraction` + `pluginChanges.html`。
4. **图标必须提供 @2x**
    - `icons/agents/<agent>.png` + `<agent>@2x.png` 两份。新增 Agent 时一份都不能少，否则 HiDPI 屏会模糊。
5. **Agent Skills 目录名取自 frontmatter 的 `name`**
    - 不再用 `skillsjars__org__repo__` 扁平前缀；与 `skillsjars-maven-plugin` v0.0.7 起的 `useSkillsNameAsDirectory=true`
      对齐。改导出命名前先确认这条约定还没失效。
6. **不要静默修改用户构建文件**
    - 任何写 `pom.xml` / `build.gradle(.kts)` 的能力（三期未实现）必须先弹 diff 让用户确认。
7. **新增 service 按官方决策路径二选一**（**完整规则见
   [`.claude/skills/add-project-service/SKILL.md`](.claude/skills/add-project-service/SKILL.md)**）
    - **有独立接口、对外暴露 API** → `plugin.xml` 的 `<projectService serviceInterface= serviceImplementation=>`。
      本项目的 `SkillRegistry` / `SkillExportService` 都是这条路径，第三方插件通过 `project.getService(<接口>.class)`
      按接口查找；light service 只支持按实现类查找，跟这种契约不兼容。
    - **无独立接口、仅本插件内部使用** → `@Service(Service.Level.PROJECT)` light service，
      **不要**再在 `plugin.xml` 注册。本项目的 `InstallationRegistryService` 是这条路径的范本。
    - 两种方式**互斥**：接口上更**不允许**标 `@Service`（IDEA 报 "Light service must be final" / "轻服务必须为具体类"）；
      实现类同时有 `@Service` + plugin.xml 注册也会报 "@Service 注解的服务类不得在 plugin.xml 中注册"。
    - 看到 IDE inspection **"服务可被转换为轻服务" / "A service can be converted to a light one"**，
      就是 IDEA 在告诉你这个 service 应该走第 2 条 light service 路径，**不要忽略**。
    - Light service 构造器**禁止** `getService(...)` 别的依赖 service（即使不存字段也是反模式）；
      用 lazy init + `AtomicBoolean` 守门，参考 `InstallationRegistryService.ensureSubscribed()`。

---

## 9. 测试约定

- 测试目录镜像生产包结构：`parser` / `api/model` / `export` / `toolwindow`。
- 单元测试默认**脱离 IDE Platform**，纯 JUnit 5 + AssertJ + Mockito。看 `ExportPlannerTest` 是范本。
- `SkillJarParserTest` 在 `build/` 临时目录里造小 JAR 验证；不要引入外部 fixture JAR。
- 提交前至少跑：
  ```bash
  ./gradlew test
  ./gradlew verifyPlugin   # 改 sinceBuild / 平台 API 后必跑
  ```

---

## 10. 多 Agent 协作建议

- **改动前先看 §4 入口表**：定位到自己要动的包，再读对应包的 1-2 个核心类（看顶部模块注释）。
- **跨层改动需要拆分**：例如"新增一个目标 Agent"会同时触及 `api/model` + `icons` + `messages` + `toolwindow`，建议拆成 1
  个提交一条线索（先模型 → 再资源 → 再 UI），便于 review。
- **修改 `api/` 必须考虑兼容**：增量加方法 OK，改签名 / 删类需要在 `pluginChanges.html` 写出 migration note。
- **修改 `includes/*.html` 后必跑一次 `runIde` 肉眼验证**：因为受 §8.1 渲染坑影响，不能只看源文件。
- **大重构请先在 `docs/` 下落一份 ADR**：`docs/design.md` 是一/二期事实来源，三期计划在 `docs/phase3-publish.md`。新提议放到
  `docs/adr/<编号>-<标题>.md`，不要直接覆盖现有设计文档。
- **永远不要**：
    - 跳过 `SkillRegistry` 直接让 UI 持有 `Scanner` / `Parser` 引用。
    - 在 `ExportExecutor` 里加业务判断（应该全部在 `ExportPlanner`）。
    - 给 `api/` 包加 IntelliJ Platform 之外的第三方依赖（保持公共契约干净）。
    - 在 `pluginDescription.html` / `pluginChanges.html` 里使用 `&xxx;` 形式的 HTML 实体。

---

## 11. 相关文档

- 项目设计事实来源：[`docs/design.md`](docs/design.md)
- 三期需求草案：[`docs/phase3-publish.md`](docs/phase3-publish.md)
- **第三方插件接入 SkillsJars Helper 的能力**：[`docs/extension-points.md`](docs/extension-points.md)（人类可读）+ [`.claude/skills/integrate-skillsjars-helper`](.claude/skills/integrate-skillsjars-helper/SKILL.md)（AI 友好）
- 项目对外介绍：[`README.md`](README.md)
- 对外 landing page：[`landing/README.md`](landing/README.md)（静态 HTML, 中英双语, `deploy.sh -d` 一键部署）
- 部署脚本：[`deploy.sh`](deploy.sh)（顶部 CONFIG 区有必改项, 用法 `./deploy.sh -h`）
- 插件市场页源文：[`includes/pluginDescription.html`](includes/pluginDescription.html)
- 变更日志源文：[`includes/pluginChanges.html`](includes/pluginChanges.html)
- 上游生态：[SkillsJars](https://www.skillsjars.com/)
- [作者其他插件](https://plugins.jetbrains.com/vendor/9afaba35-91ea-4364-8ced-64db868dd23e)
