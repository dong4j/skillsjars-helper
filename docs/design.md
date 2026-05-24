# SkillsJars Helper 设计文档

## 背景

SkillsJars 的核心价值是把 Agent Skill 打包进 Jar，并通过 Maven/Gradle 依赖体系完成分发、版本管理和提取。它让 Skill 可以像代码依赖一样发布到
Maven Central、私服或本地仓库。

但在 IDE 侧，开发者仍然缺少一个直接入口：

- 不知道当前项目依赖里有哪些 Skill。
- 不知道 Skill 来自普通依赖还是 Maven plugin dependencies。
- 不方便在 IDE 内搜索 `skillsjars.com` 上已经发布的 SkillsJar。
- 不方便根据 Maven、Gradle、sbt 项目类型选择正确依赖写法。
- 不方便直接查看 Jar 内的 `SKILL.md`。
- 不方便把 Skill 导出到不同 Agent 工具目录。
- 不方便把当前项目里的 `skills/` 校验、打包并发布成 SkillsJar。
- 不容易判断导出后的目录是否过期、冲突或被手工修改。

SkillsJars Helper 的设计目标是补上这个 IDE 体验空位。

## 设计原则

### 1. 先发现，再导出

插件必须支持直接从 classpath/Jar 读取 Skill 元数据。导出是后续动作，不是查看 Skill 的前提。

### 2. 尊重用户目录

插件可以检测 `.claude/skills`、`.codex/skills` 等候选目录，但不能静默写入。任何写入 Agent Skill 目录的操作都需要用户确认。

### 3. 兼容 SkillsJars 约定

第一优先级是兼容 SkillsJars 现有约定路径：

```text
META-INF/skills/<org>/<repo>/<skill>/SKILL.md
META-INF/resources/skills/<org>/<repo>/<skill>/SKILL.md
```

后续可以扩展 Zeka 自定义索引或其他路径，但不应影响对标准 SkillsJar 的识别。

### 4. UI 和服务分离

扫描、索引、解析、导出和状态追踪应作为 project-level service 提供，Tool Window 只是一个消费方。这样其他 IDEA 插件也可以通过扩展接口复用能力。

### 5. AI 是增强，不是基础依赖

Skill 生成可以接入 IntelliAI Engine，但扫描、预览、导出和扩展接口不依赖 AI 插件。

### 6. 写入前必须展示变更

搜索市场并安装依赖、导出 Skill、生成发布配置都会修改用户项目文件或目标目录。插件必须在写入前展示目标文件、目标目录和变更摘要。

## 核心场景

### 场景 1：查看当前项目可用 Skill

用户打开 Java 项目后，插件扫描 Maven/Gradle 依赖和 External Libraries，在 Tool Window 中展示所有发现的 Skill。

目标体验：

```text
pom.xml 引入 SkillsJar 后
→ IDEA 自动刷新依赖
→ Agent Skills Tool Window 出现 Skill 列表
→ 用户可以直接预览 SKILL.md
```

### 场景 2：导出 Skill 到 Agent 目录

用户选择一个 Skill 或一个来源 Artifact，右键导出到目标 Agent 目录。

目标体验：

```text
Extract to...
├── .claude/skills
├── .codex/skills
├── .junie/skills
├── .agents/skills
├── .cursor/skills
├── .gemini/skills
├── .qoder/skills
├── .trae/skills
└── Custom Directory...
```

导出前需要检查：

- 目标目录是否存在。
- 目标 Skill 是否已存在。
- 是否由本插件管理。
- 来源版本是否变化。
- 本地文件是否被用户修改。

### 场景 3：发现 Maven plugin dependencies 中的 Skill

SkillsJars 官方使用方式中，Skill 可能配置在 `skillsjars-maven-plugin` 的 `<dependencies>` 下：

```xml
<plugin>
    <groupId>com.skillsjars</groupId>
    <artifactId>maven-plugin</artifactId>
    <dependencies>
        <dependency>
            <groupId>example.group</groupId>
            <artifactId>example-skills</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>
</plugin>
```

这类 Jar 不一定出现在普通 project dependencies 中，所以插件必须单独扫描 Maven plugin dependencies。

### 场景 4：为当前模块生成 Skill

用户在 Java module 上触发生成动作，插件创建一个 `SKILL.md` 初始模板。

基础模板应包含：

- `name`
- `description`
- `allowed-tools`
- 适用模块说明
- 使用边界
- 输出要求

如果 IntelliAI Engine 可用，可以读取模块上下文生成更完整内容。

### 场景 5：搜索并安装已发布 SkillsJar

用户在 Tool Window 或独立 Marketplace 面板中搜索 `skillsjars.com` 上已发布的 SkillsJar。

官网当前支持按查询条件搜索，并提供 Maven、Gradle、sbt 三种依赖片段。插件需要把这个能力放进 IDE，并根据当前项目构建系统自动选择默认写法。

目标体验：

```text
Search: code review
→ 展示 SkillsJar 名称、描述、安全扫描状态、版本列表、坐标
→ 自动识别当前项目是 Maven / Gradle / sbt
→ 展示对应依赖片段
→ Copy Dependency 或 Install Dependency
```

安装前需要检查：

- 当前项目构建系统。
- 依赖应该写入普通 dependencies，还是 `skillsjars-maven-plugin` 的 plugin dependencies。
- 是否已存在相同 Artifact。
- 是否需要更新版本。
- 是否需要触发 Maven/Gradle/sbt 重新导入。

### 场景 6：一键发布当前项目的 SkillsJar

用户在当前项目中维护 `skills/` 目录，并希望把这些 Skill 发布成 SkillsJar。

官网当前首页提供 `Publish a SkillsJar` 表单，字段是 GitHub Org 和 GitHub Repo；官方文档也描述了 public GitHub repository 的发布方式。SkillsJars
Helper 需要兼容这个路径，但不能把 GitHub 仓库作为唯一前提。

目标体验：

```text
Publish SkillsJar...
→ 检测 skills/ 目录
→ 校验每个 SKILL.md
→ 检查 frontmatter / allowed-tools / license / 引用文件
→ 配置 groupId / artifactId / version / 发布目标
→ 生成或更新 Maven package 配置
→ 运行本地打包验证
→ 用户确认后发布或打开官网 GitHub Deploy 流程
```

发布路径分为两类：

| 路径                 | 说明                                                                    |
|--------------------|-----------------------------------------------------------------------|
| GitHub Deploy      | 对齐官网 `Publish a SkillsJar` 表单，适合公开 GitHub 仓库                          |
| Local Publish Prep | 适合非 GitHub 项目，插件负责校验、打包、生成 POM/发布配置，上传动作交给用户配置的 Maven Central 或私服发布链路 |

第一版不直接保存仓库凭据，也不静默上传制品。

## 功能模块

```text
IDEA Project
  |
  |-- Skill Source Scanner
  |     |-- Maven Dependency Scanner
  |     |-- Maven Plugin Dependency Scanner
  |     |-- Gradle Dependency Scanner
  |     |-- External Library Scanner
  |     `-- Local Jar Scanner
  |
  |-- Marketplace Service
  |     |-- SkillsJars.com Search Client
  |     |-- Dependency Snippet Renderer
  |     |-- Project Build Tool Detector
  |     `-- Dependency Installer
  |
  |-- Skill Parser
  |     |-- Jar Entry Locator
  |     |-- SKILL.md Reader
  |     `-- Frontmatter Parser
  |
  |-- Skill Index Service
  |     |-- Cache
  |     |-- Refresh
  |     `-- Change Events
  |
  |-- Tool Window
  |     |-- List
  |     |-- Detail
  |     |-- Preview
  |     `-- Actions
  |
  |-- Export Service
  |     |-- Target Directory Detector
  |     |-- Conflict Checker
  |     |-- File Extractor
  |     `-- Manifest Writer
  |
  |-- Inspection Service
  |     |-- Frontmatter Check
  |     |-- Duplicate Name Check
  |     |-- Allowed Tools Risk Check
  |     `-- Broken Reference Check
  |
  |-- Generator Service
  |     |-- Template Generator
  |     `-- IntelliAI Engine Adapter
  |
  |-- Publish Service
  |     |-- Skills Directory Validator
  |     |-- Package Configuration Generator
  |     |-- Local Package Verifier
  |     `-- Publish Workflow Adapter
  |
  `-- Extension API
        `-- Public Query / Export / Event Contracts
```

## 数据模型草案

```kotlin
data class SkillJarArtifact(
    val groupId: String?,
    val artifactId: String?,
    val version: String?,
    val file: VirtualFile,
    val sourceType: SkillSourceType,
    val skills: List<SkillDescriptor>
)

enum class SkillSourceType {
    MAVEN_DEPENDENCY,
    MAVEN_PLUGIN_DEPENDENCY,
    GRADLE_DEPENDENCY,
    PROJECT_OUTPUT,
    EXTERNAL_LIBRARY,
    LOCAL_JAR
}

data class SkillDescriptor(
    val name: String,
    val description: String?,
    val allowedTools: List<String>,
    val license: String?,
    val artifact: SkillJarArtifact,
    val jarEntryRoot: String,
    val skillMdPath: String,
    val files: List<String>,
    val riskLevel: RiskLevel,
    val installState: SkillInstallState
)

data class MarketplaceSkill(
    val name: String,
    val description: String,
    val groupId: String,
    val artifactId: String,
    val versions: List<String>,
    val securityScanned: Boolean,
    val sourceUrl: String?
)

enum class BuildTool {
    MAVEN,
    GRADLE,
    SBT,
    UNKNOWN
}

data class DependencySnippet(
    val buildTool: BuildTool,
    val scope: String,
    val text: String
)
```

这个模型刻意把 `SkillDescriptor` 和 UI 状态分离。UI 可以按来源、风险、安装状态做分组，但底层查询接口仍返回稳定结构。

## Jar 扫描规则

扫描 Jar 时只把包含 `SKILL.md` 的目录识别为一个 Skill。

匹配路径：

```text
META-INF/skills/**/SKILL.md
META-INF/resources/skills/**/SKILL.md
```

一个 Skill 的资源根目录是 `SKILL.md` 所在目录。导出时默认只复制这个根目录下的文件。

示例：

```text
META-INF/skills/dev/dong4j/zeka-code-review/SKILL.md
META-INF/skills/dev/dong4j/zeka-code-review/examples/review.md
```

对应 Skill root：

```text
META-INF/skills/dev/dong4j/zeka-code-review/
```

## Tool Window 设计

第一版使用表格即可，不需要复杂树形 UI。

建议列：

| 列             | 说明                                              |
|---------------|-------------------------------------------------|
| Skill         | `name` 或目录名                                     |
| Description   | frontmatter 描述                                  |
| Source        | Maven / Maven Plugin / Gradle / Local Jar       |
| Artifact      | `groupId:artifactId:version`                    |
| Allowed Tools | `allowed-tools`                                 |
| Risk          | Low / Medium / High                             |
| Installed     | Not Installed / Installed / Outdated / Modified |

右键动作：

- Preview `SKILL.md`
- Extract to Claude
- Extract to Codex
- Extract to Junie
- Extract to Shared `.agents`
- Extract to Custom Directory
- Copy Skill Name
- Copy Maven Coordinate
- Open Source Jar
- Refresh Source

Marketplace 面板建议字段：

| 列           | 说明                           |
|-------------|------------------------------|
| Skill       | 官网展示名称                       |
| Description | 官网描述                         |
| Coordinate  | `groupId:artifactId:version` |
| Versions    | 可选版本                         |
| Security    | 是否 Security Scanned          |
| Build Tool  | Maven / Gradle / sbt         |

Marketplace 动作：

- Search
- Copy Dependency
- Install Dependency
- Preview Snippet
- Open on SkillsJars.com
- Add to Maven Plugin Dependencies

## 导出策略

### 命名规则

目录命名直接采用 `SKILL.md` frontmatter 的 `name` 字段，与 Agent Skills 规范保持一致：

| 场景            | 目录名示例                            | 说明                                                        |
|---------------|---------------------------------------|-----------------------------------------------------------|
| 默认            | `code-review/`                       | 直接采用 frontmatter 的 `name`                              |
| 缺失 `name`     | `code-review/`                       | 退化到 jar 内 skill 根目录最后一段 (parser 已 fallback)  |
| DUPLICATE_NAME | `code-review__zeka-skills/`         | 同名冲突时 fallback 为 `<name>__<artifactId>`              |

**为什么不沿用官方 `skillsjars__org__repo__skill` 扁平化前缀**：

- Anthropic 的 Agent Skills 规范以及主流 code assistant (Claude Code / Cursor / Codex 等)
  期望目录名 == frontmatter `name`，参考 [skillsjars-maven-plugin#4](https://github.com/skillsjars/skillsjars-maven-plugin/issues/4)。
- v0.0.7 的 `useSkillsNameAsDirectory=true` 已经是官方背书。
- 维护者自己也指出该选项有同名冲突副作用，但 Maven CLI 没有 IDE 上下文无法很好地解决。
  本插件天然有完整 IDE 上下文，可以专门处理同名冲突 (见下文 `DUPLICATE_NAME`)。

### 写入交互 (6 状态机)

导出前由 `ExportPlanner` 计算 `InstallationStatus`，UI 按状态决定弹什么：

| 状态                  | 触发条件                                                                  | 行为                                                                      |
|---------------------|-----------------------------------------------------------------------|-------------------------------------------------------------------------|
| `NEW`               | 目标目录不存在                                                              | 直接写 + Notification "已安装"                                               |
| `UP_TO_DATE`        | 已存在 + manifest 来源一致 + jar sha 一致 + 所有文件 sha 一致                  | 不写 + Notification "已是最新"                                              |
| `OUTDATED`          | 已存在 + manifest 来源一致 + jar sha 升级了                                | 直接覆盖 + Notification "已升级"                                            |
| `LOCALLY_MODIFIED`  | 已存在 + manifest 来源一致 + 本地文件 sha 与 manifest 不符                    | yes/no 弹窗 "本地修改将丢失，是否覆盖？"                                          |
| `FOREIGN`           | 已存在 + 没有 manifest                                                  | yes/no 弹窗 "目录不是由本插件管理，是否覆盖？"                                       |
| `DUPLICATE_NAME`    | 已存在 + manifest 来源是 **另一个 jar / 另一个 entry root** (同名不同源)         | 三选项弹窗：① 覆盖原 skill ② 改用 `<name>__<artifactId>` 后缀名 ③ 取消             |

`UP_TO_DATE` 与 `OUTDATED` 默认不弹窗以保证常用路径的流畅；其余三种都需要二次确认以避免误覆盖用户内容。

### 写入算法

1. 准备 Agent 父目录 (如 `.claude/skills`) 不存在则递归创建。
2. 在父目录下创建临时目录 `<target>.tmp-<random>`，把 jar 内的 skill 内容复制进去并同时计算每文件 sha256。
3. 写 `.skillsjars-helper.json` 到临时目录。
4. 如果目标目录已存在，递归删除。
5. 用 atomic move 把临时目录改名为最终目录；不支持 atomic 的文件系统 (跨设备 / 部分 Windows) 退化为普通 move。
6. 通过 `LocalFileSystem.refreshAndFindFileByNioFile` 把变更同步到 IDEA VFS，让项目视图立即看到。

任何步骤异常都会清理临时目录，不会留下半成品。

## SkillsJars.com 搜索与安装

插件需要封装一个 Marketplace Service，负责访问 `https://www.skillsjars.com` 的搜索结果并生成 IDE 内模型。

官网当前页面提供：

- 搜索框：按名称或描述搜索。
- 构建工具切换：Maven、Gradle、sbt。
- 版本选择。
- Copy 动作。
- Security Scanned 状态。

插件侧应抽象为：

```text
query + buildTool
→ MarketplaceSkill list
→ selected version
→ DependencySnippet
```

依赖片段示例：

```xml
<dependency>
    <groupId>com.skillsjars</groupId>
    <artifactId>example-skill</artifactId>
    <version>2026_04_03-a806c63</version>
</dependency>
```

```kotlin
runtimeOnly("com.skillsjars:example-skill:2026_04_03-a806c63")
```

```scala
"com.skillsjars" % "example-skill" % "2026_04_03-a806c63"
```

项目类型识别：

| 文件                                  | BuildTool |
|-------------------------------------|-----------|
| `pom.xml`                           | Maven     |
| `build.gradle` / `build.gradle.kts` | Gradle    |
| `build.sbt`                         | sbt       |

安装策略：

| 项目类型                       | 写入位置                                         |
|----------------------------|----------------------------------------------|
| Maven AI Code Assistant 场景 | `skillsjars-maven-plugin` 的 `<dependencies>` |
| Maven Custom Agent 场景      | 普通 `<dependencies>`                          |
| Gradle                     | `dependencies { runtimeOnly(...) }`          |
| sbt                        | `libraryDependencies += ...`                 |

Maven 场景需要让用户选择“加入普通依赖”还是“加入 SkillsJars 插件依赖”，默认优先插件依赖，因为 AI Code Assistant 场景需要后续执行
`skillsjars:extract`。

安装前必须展示变更预览。安装后可提示刷新 Maven/Gradle/sbt 项目。

## 一键发布设计

发布能力分为检查、打包、发布准备和发布执行四层。

### 发布前检查

检查内容：

| 检查项             | 说明                          |
|-----------------|-----------------------------|
| `skills/` 目录    | 默认检查项目根目录下的 `skills/`       |
| Skill 子目录       | 每个直接子目录视为一个 Skill           |
| `SKILL.md`      | 每个 Skill 必须包含标记文件           |
| `name`          | frontmatter 必填              |
| `description`   | frontmatter 必填              |
| `allowed-tools` | 可选，但需要能解析为空格分隔列表            |
| `license`       | 可选，但发布前应提示缺失                |
| 引用文件            | Markdown 链接、脚本、模板路径不能指向缺失文件 |

### 打包配置

插件应能生成或更新 SkillsJars Maven package goal 配置：

```xml
<plugin>
    <groupId>com.skillsjars</groupId>
    <artifactId>maven-plugin</artifactId>
    <version>0.0.6</version>
    <executions>
        <execution>
            <goals>
                <goal>package</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

如果使用自定义 skills 目录，需要生成 `skillsDir` 配置。所有 POM 改动同样需要先展示 diff。

### 发布路径

| 路径                 | 适用条件                   | 插件职责                          |
|--------------------|------------------------|-------------------------------|
| GitHub Deploy      | 项目已公开托管在 GitHub        | 校验后打开或提交官网 Deploy 参数          |
| Local Publish Prep | 非 GitHub 项目或企业私有项目     | 校验、打包、生成 POM/发布配置和命令提示        |
| Private Repository | 企业 Nexus / Artifactory | 读取用户已有 Maven publish 配置，不保存凭据 |

### 发布状态

发布向导需要明确展示：

- 当前项目坐标。
- 即将发布的 Skill 列表。
- 版本号。
- 发布目标。
- 是否符合 `SKILL.md` 规范。
- 是否完成本地 `mvn package` 验证。

第一版可以先实现到“发布准备完成”，即生成配置和命令，不直接上传到远端仓库。

## Manifest 设计

导出后在 Skill 目录内写入 `.skillsjars-helper.json`：

```json
{
  "schemaVersion": 1,
  "managedBy": "skillsjars-helper",
  "artifact": "dev.dong4j:zeka-skills:1.0.0",
  "sourceType": "MAVEN_DEPENDENCY",
  "skill": "code-review",
  "sourceJarSha256": "...",
  "skillRoot": "META-INF/skills/dev/dong4j/code-review/",
  "installedAt": "2026-05-23T10:00:00+08:00",
  "targetAgent": "claude",
  "targetPath": "/abs/path/.claude/skills/code-review",
  "files": [
    {
      "path": "SKILL.md",
      "sha256": "...",
      "size": 1234
    }
  ]
}
```

字段含义：

| 字段                | 用途                                                                                                |
|------------------|--------------------------------------------------------------------------------------------------|
| `schemaVersion`  | manifest 自身版本号，向后不兼容时递增                                                                              |
| `managedBy`      | 固定为 `skillsjars-helper`，用于判断是否本插件管理 (区分 `FOREIGN`)                                          |
| `artifact`       | 来源坐标，与 `skillRoot` 联合识别 skill 来源 (用于判断 `DUPLICATE_NAME`)                                      |
| `sourceType`     | 来源类型枚举，仅记录便于审计                                                                                      |
| `skill`          | frontmatter `name` 的快照                                                                              |
| `sourceJarSha256`| **当前 skill 的内容指纹** (不是整个 jar 的 sha)，由 skill 根目录下所有文件的 (path + sha) 聚合再 sha 计算得到 |
| `skillRoot`      | jar 内 skill 根路径                                                                                     |
| `installedAt`    | ISO-8601 + 时区，仅供人读                                                                                   |
| `targetAgent`    | Agent ID (`claude` / `codex` / `junie` / `agents` / `cursor` / `gemini` / `qoder` / `trae` / `custom`) |
| `targetPath`     | 落盘绝对路径                                                                                              |
| `files`          | 每个文件的 (relative-path, sha256, size)，用于 `LOCALLY_MODIFIED` 判定                                    |

为什么 `sourceJarSha256` 不用整个 jar 的 sha：同 jar 内其他 skill 的变化不应触发本 skill 的 `OUTDATED`，
也不能仅用 `SKILL.md` 的 sha 因为同 skill 内的 `examples/` `scripts/` 等辅助文件变化也应触发升级。

JSON 序列化使用本插件自带的 mini parser，避免引入 Gson 等第三方依赖；解析失败时把目录视为 `FOREIGN`
(损坏的 manifest ≈ 没有可信 manifest)。

## 风险检查

第一版只做轻量风险提示，不做重型安全审计。

建议规则：

| 风险     | 条件                             |
|--------|--------------------------------|
| Low    | 只包含 `Read`、`Grep`、`Glob` 等读取工具 |
| Medium | 包含 `Edit`、`Write`、`MultiEdit`  |
| High   | 包含 `Bash`、网络命令提示、删除文件提示、凭据相关文本 |

风险提示只影响用户决策，不阻止导出。企业安全策略后续可以单独扩展。

## 扩展接口

插件需要提供面向其他 IDEA 插件的查询服务。

初步接口方向：

```kotlin
interface SkillRegistry {
    fun getSkills(project: Project): List<SkillDescriptor>
    fun findByName(project: Project, name: String): List<SkillDescriptor>
    fun findByArtifact(project: Project, coordinate: String): List<SkillDescriptor>
    fun addListener(project: Project, listener: SkillRegistryListener): Disposable
}

interface SkillExportService {
    fun detectTargets(project: Project): List<SkillTargetDirectory>
    fun exportSkill(project: Project, skill: SkillDescriptor, target: SkillTargetDirectory): SkillExportResult
}

interface SkillsJarsMarketplace {
    fun search(query: String, buildTool: BuildTool): List<MarketplaceSkill>
    fun renderSnippet(skill: MarketplaceSkill, version: String, buildTool: BuildTool): DependencySnippet
}

interface SkillsJarPublisher {
    fun validate(project: Project): SkillsJarValidationResult
    fun preparePackage(project: Project, options: PublishOptions): PublishPreparationResult
}
```

接口设计约束：

- 不直接暴露 Tool Window 组件。
- 不要求调用方理解 Jar 扫描细节。
- 返回值需要表达错误原因，例如冲突、权限不足、目标目录不存在。
- Marketplace 和 Publisher 接口不能要求调用方依赖 Tool Window。

## 与 IntelliAI Engine 的关系

SkillsJars Helper 可以和 IntelliAI Engine 集成，但不依赖它。

集成点：

- 为当前模块生成 `SKILL.md`。
- 根据扫描到的 Skill 给当前 AI 会话推荐上下文。
- 把选中的 Skill 内容注入到 IntelliAI Engine 的上下文系统。
- 根据 `allowed-tools` 或风险等级提示用户谨慎使用。

基础插件仍应在没有 IntelliAI Engine 的 IDEA 环境中正常运行。

## MVP 实施顺序

1. 建立 Gradle IntelliJ Plugin 项目骨架。
2. 定义 Skill 数据模型。
3. 实现 Jar 扫描和 `SKILL.md` frontmatter 解析。
4. 接入 Maven 普通依赖扫描。
5. 接入 Maven plugin dependencies 扫描。
6. 实现 project-level Skill Index Service。
7. 实现 Tool Window 表格展示。
8. 实现 `SKILL.md` 预览。
9. 实现导出到指定目录。
10. 写入 manifest 并支持安装状态判断。
11. 实现 SkillsJars.com 搜索和三类依赖片段渲染。
12. 实现 Maven 项目的依赖安装预览。
13. 实现 `skills/` 目录发布前检查。
14. 实现 Maven package 配置生成。
15. 补充基础测试样例 Jar。

## 一期实施现状（解析链路落地说明）

> 当前仓库已经完成 1–7 步对应的最小闭环；导出 / 安装 / 发布相关章节仍属于规划文档。

### 已落地的代码结构

```text
src/main/java/dev/dong4j/idea/skillsjars/helper/
├── api/                        # 公共 API（面向第三方插件复用）
│   ├── SkillRegistry.java
│   ├── SkillRegistryListener.java
│   └── model/
│       ├── SkillCoordinate.java
│       ├── SkillDescriptor.java
│       ├── SkillJarArtifact.java
│       └── SkillSourceType.java
├── scanner/                    # 扩展点 + 内置扫描器
│   ├── SkillSourceScanner.java        # 扩展点 EP
│   ├── ScanContext.java
│   ├── SkillJarSource.java
│   ├── AbstractLibraryScanner.java
│   ├── MavenLibraryScanner.java       # 一期 EP 实现 1
│   └── MavenPluginDependencyScanner.java # 一期 EP 实现 2（可选 Maven 集成）
├── parser/                     # SKILL.md 解析
│   ├── SkillFrontmatterParser.java
│   ├── ParsedSkillMd.java
│   └── SkillJarParser.java
├── service/
│   └── SkillRegistryService.java      # 项目级 Service，实现 SkillRegistry
└── toolwindow/
    ├── SkillsToolWindowFactory.java
    ├── SkillsToolWindowPanel.java
    └── SkillsTableModel.java
```

### 调用链

```text
ToolWindow 打开 / Refresh
    └── SkillRegistry.refresh()
        └── ProgressManager (后台线程)
            └── 遍历 SkillSourceScanner EP
                ├── MavenLibraryScanner          → 普通 <dependency>
                └── MavenPluginDependencyScanner → plugin 内 <dependencies>
            └── 协调层按 jar 路径去重
            └── SkillJarParser
                ├── 仅识别 META-INF/skills/**/SKILL.md
                ├── 仅识别 META-INF/resources/skills/**/SKILL.md
                └── SkillFrontmatterParser
            └── 写入 AtomicReference<List<SkillJarArtifact>>
            └── invokeLater 派发监听器
                └── ToolWindow 表格刷新
```

### 扩展点契约

外部插件（含未来的 `GradleLibraryScanner`）只需实现
`dev.dong4j.idea.skillsjars.helper.scanner.SkillSourceScanner`，并在自己的 `plugin.xml` 中
注册到扩展点：

```xml
<extensions defaultExtensionNs="dev.dong4j.idea.skillsjars.helper">
    <skillSourceScanner implementation="..." />
</extensions>
```

调用方读取扫描结果走 `SkillRegistry.getInstance(project)`，事件订阅走
`addListener` 返回的 `Disposable`。

### 一期的边界

- 仅识别 `META-INF/skills/**/SKILL.md` 与 `META-INF/resources/skills/**/SKILL.md`。
- 仅 Maven 项目；Gradle 库虽然 `SkillCoordinate` 已支持解析 `Gradle:` 前缀，但二期才提供
  对应的 `GradleLibraryScanner`。
- Tool Window 仅五列：Skill / Description / Source / Artifact / Allowed Tools；
  Risk / Installed 两列留给后续阶段。
- 没有写入 / 导出 / 发布动作，所有动作只读。

## 暂不处理的问题

- 完整 Gradle 依赖图解析。
- 企业级安全扫描。
- 在线 Skill 仓库。
- 多版本 Skill 自动升级策略。
- 跨项目共享缓存。
- IntelliAI Engine 深度上下文注入。
- 自动保存 Maven Central、Nexus、Artifactory 发布凭据。
- 非 GitHub 项目的完整远端发布执行。

这些能力可以在 MVP 稳定后再拆分里程碑。
