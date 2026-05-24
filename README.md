# SkillsJars Helper

SkillsJars Helper 是一个面向 JetBrains IDE 的 Agent Skill 依赖管理插件。它让 Java 项目中通过 SkillsJars 分发的 `SKILL.md` 可以在 IDEA
内被发现、预览、检查、导出和复用。

这个项目的核心判断是：SkillsJars 已经解决了“如何把 Agent Skill 打进 Jar 并通过 Maven/Gradle 分发”的问题，但开发者在 IDE 里仍然缺少一个可视化入口来回答这些问题：

- 当前项目 classpath 里到底有哪些 Skill？
- 它们来自哪个 Maven/Gradle 依赖或插件依赖？
- `skillsjars.com` 上有哪些已经发布的 SkillsJar，可以直接安装到当前项目？
- 每个 Skill 的 `name`、`description`、`allowed-tools`、来源 Jar 和内部路径是什么？
- 能否一键导出到 Claude Code、Codex、Junie、Cursor 等 Agent 的 Skill 目录？
- 当前项目里的 `skills/` 能否一键校验、打包并发布成 SkillsJar？
- 导出后是否有冲突、更新、覆盖或本地修改？

## 产品定位

SkillsJars Helper 不是另一个解压工具，而是：

> Java 项目的 AI 上下文依赖管理器。

它把 Agent Skill 当成一种可管理的工程依赖，让它像普通 Jar 一样被 IDE 识别、索引和展示，同时保留向不同 Agent 工具导出的能力。

## 核心能力

### Skill 发现

插件会扫描当前项目可见的 Skill 来源：

- Maven 普通依赖
- Maven plugin dependencies
- Gradle dependencies
- IDEA External Libraries
- 当前 module output
- 用户手动选择的本地 Jar

识别规则优先覆盖 SkillsJars 约定路径：

```text
META-INF/skills/**/SKILL.md
META-INF/resources/skills/**/SKILL.md
```

其中 Maven plugin dependencies 是第一版必须关注的来源，因为 SkillsJars 官方用法经常把 SkillsJar 配在 `skillsjars-maven-plugin` 的
`<dependencies>` 下，而不是普通项目依赖中。

### SkillsJar 市场搜索与安装

插件会接入 `https://www.skillsjars.com` 上已发布的 SkillsJar 列表，支持按名称、描述、Artifact、版本和安全扫描状态搜索。

搜索结果需要提供三类依赖写法：

- Maven
- Gradle
- sbt

插件应根据当前项目类型默认展示最合适的依赖片段：

- Maven 项目展示 `<dependency>` 或 `skillsjars-maven-plugin` 的 `<dependencies>` 写法。
- Gradle 项目展示 `runtimeOnly("group:artifact:version")` 写法。
- sbt 项目展示 `"group" % "artifact" % "version"` 写法。

每个搜索结果至少提供两个动作：

- Copy Dependency：复制当前项目类型对应的依赖片段。
- Install Dependency：把依赖写入当前项目构建文件，并触发 IDE 重新导入。

安装动作必须先展示 diff 或变更预览，避免静默修改 `pom.xml`、`build.gradle(.kts)` 或 `build.sbt`。

### Tool Window 展示

插件提供 `Agent Skills` Tool Window，用列表或分组树展示 Skill。

每个 Skill 条目至少包含：

- Skill 名称
- 描述
- 来源类型
- Maven/Gradle 坐标
- 来源 Jar
- Jar 内部路径
- `allowed-tools`
- 风险等级
- 是否已导出

### Skill 预览与检查

插件支持直接从 Jar 中预览 `SKILL.md`，不要求先导出到文件系统。

后续会增加基础检查能力：

- frontmatter 是否完整
- `name` 是否重复
- `description` 是否为空或过短
- `allowed-tools` 是否包含高风险权限
- Markdown 引用的文件是否存在
- Skill 目录资源是否完整

### Skill 导出

插件支持将 Jar 内的 `SKILL.md` 及其相关文件导出到指定 Agent Skill 目录。

候选目录可以自动检测，但写入前必须由用户确认：

- `.claude/skills`
- `.codex/skills`
- `.junie/skills`
- `.agents/skills`
- `.cursor/skills`
- `.gemini/skills`
- 自定义目录

导出时会生成 manifest，用来追踪来源 Jar、Artifact、版本、目标路径和文件哈希，方便后续判断是否需要更新、清理或提示冲突。

### Skill 生成

插件可以为当前选中的 Java 模块生成 `SKILL.md` 初始模板，方便后续通过 SkillsJars 打包。

生成能力分为两层：

- 基础生成：根据模块名、包结构、README、源码入口等信息生成模板。
- AI 增强：可选集成 IntelliAI Engine 插件，根据项目上下文生成更完整的触发规则、使用边界和说明。

AI 能力不是插件强依赖。没有 IntelliAI Engine 时，插件仍应保留基础模板生成能力。

### SkillsJar 发布

插件提供一键发布入口，用于把当前项目中的 `skills/` 目录校验、打包并发布成 SkillsJar。

发布前必须完成规范检查：

- 每个 Skill 子目录必须包含 `SKILL.md`。
- `SKILL.md` 必须符合 Agent Skills 规范。
- frontmatter 必须包含 `name` 和 `description`。
- `allowed-tools` 和 `license` 需要按 SkillsJars 约定处理。
- 关联文件必须存在，不能引用缺失资源。

官网当前提供 `Publish a SkillsJar` 表单，并以 GitHub Org / GitHub Repo 作为入口。SkillsJars Helper 不应把“项目必须在 GitHub
上”作为唯一发布路径；插件应同时支持：

- GitHub 发布：兼容官网表单，适合公开 GitHub 仓库。
- 本地发布准备：对非 GitHub 项目执行校验、打包、生成 POM/发布配置，并交给用户配置的 Maven Central 或私服发布链路。

第一版可以先做到发布前检查和打包配置生成，真正上传动作需要用户确认凭据、目标仓库和版本。

### 扩展接口

插件会提供标准 IDEA Plugin 扩展接口，让其他插件复用 Skill 检索能力。

预期能力包括：

- 查询当前项目可用 Skill
- 按名称、来源 Jar、Artifact 查找 Skill
- 获取 `SKILL.md` 内容和相关资源
- 监听扫描结果变化
- 复用导出与安装状态判断逻辑

扩展接口应返回结构化模型，避免把 UI 或文件系统细节直接暴露给调用方。

## MVP 范围

第一版优先做小而完整的闭环：

1. 扫描 Maven 普通依赖 Jar。
2. 扫描 Maven plugin dependencies 中的 SkillsJar。
3. 识别 `META-INF/skills/**/SKILL.md`。
4. 解析 `name`、`description`、`allowed-tools`、`license`。
5. Tool Window 列表展示。
6. 双击预览 `SKILL.md`。
7. 右键导出到 `.claude/skills`、`.codex/skills`、`.junie/skills`、`.agents/skills`。
8. 导出时生成 manifest。
9. 提供 Refresh 按钮。
10. 提供 SkillsJars.com 搜索、复制依赖、按项目类型安装依赖。
11. 提供 SkillsJar 发布前检查和打包配置生成。

第二阶段再扩展：

- Gradle 支持
- 冲突检测
- `allowed-tools` 风险评分
- `SKILL.md` Inspection / Quick Fix
- Agent 目录自动检测配置页
- Maven/Gradle extract Run Configuration
- IntelliAI Engine 集成
- 非 GitHub 项目的完整发布执行链路
- sbt 项目的自动写入与刷新

## 非目标

- 不替代 SkillsJars Maven/Gradle 插件的打包能力。
- 不默认修改用户全局 Agent 目录。
- 不静默修改构建文件，安装依赖前必须展示变更。
- 不强制绑定某一个 Agent 工具。
- 不把 AI 生成功能作为扫描、预览、导出的前置依赖。
- 不把 GitHub 仓库作为发布 SkillsJar 的唯一前提。
- 不在第一版实现完整的企业安全审计。

## 文档

- [设计文档](docs/design.md)

## 开发状态

| 阶段 | 范围 | 状态 |
|---|---|---|
| 一期 · 解析链路 | Maven 普通依赖 + Maven plugin dependencies 扫描 → SKILL.md 解析 → ToolWindow 列表展示 | 已完成 |
| 二期 · 导出 | Skill 导出到 6 个 Agent 目录、manifest 写入、6 状态冲突机 (含 DUPLICATE_NAME) | 已完成 |
| 三期 · 发布 | SkillsJars 发布前检查 + 打包配置生成 | 计划中 |
| 三期 · Gradle 解析 | Gradle dependencies 扫描；通过同一 `SkillSourceScanner` 扩展点接入 | 计划中 |

一期把扩展接口 `SkillRegistry` 和扫描器扩展点 `dev.dong4j.idea.skillsjars.helper.skillSourceScanner` 一次性预留；
二期再加一个对称的导出 API `SkillExportService` 和一个安装索引服务 `InstallationRegistry`，
未来 Gradle 扫描器和第三方查询/导出插件可以直接复用这套接口，不需要改动协调层。

### 一期验证方式

1. 运行 `./gradlew runIde` 启动一个内嵌 IDEA。
2. 在内嵌 IDE 中打开任意 Maven 项目（`pom.xml` 中包含 SkillsJar 依赖即可）。
3. 在右侧打开 `Agent Skills` Tool Window：
   - 上半区是按 artifact 折叠的树形列表，叶子节点是命中
     `META-INF/skills/**/SKILL.md` 或 `META-INF/resources/skills/**/SKILL.md` 的 skill。
   - 下半区是描述面板，选中 skill 后展示其 `description` 全文（可拖动分割条调整高度）。
   - 双击叶子或回车直接打开 jar 内 SKILL.md；右键菜单可复制 skill 名 / 坐标 / 打开源 jar。
   - 工具栏只保留 `Refresh / Expand All / Collapse All`；底部状态栏展示全局汇总。

### 二期验证方式

1. 在内嵌 IDE 中右键 skill 叶子节点，选 `Extract to ▸`，应能看到 6 个预设 Agent 目录 + Custom Directory。
2. 选 `.claude/skills` 第一次导出 → 看到 Notification "已安装 skill ..."，
   skill 主图标右侧追加一个 Claude 品牌图标作为安装徽标（多次安装会横排追加多个 Agent 图标）。
3. 同一个 skill 再选同一个 Agent → Notification "已是最新"（UP_TO_DATE）。
4. 修改一下 `.claude/skills/<name>/SKILL.md` 再导出 → 弹 yes/no 确认 (LOCALLY_MODIFIED)。
5. 手工 `mkdir .claude/skills/foo` 但不放 manifest，再导出一个 frontmatter `name: foo` 的 skill →
   弹 yes/no 确认 (FOREIGN)。
6. 在两个不同 jar 内同时存在 `name: foo` 的 skill，先导出第一个，再导出第二个 →
   弹三选项弹窗 (DUPLICATE_NAME)：覆盖原有 / 改用 `foo__<artifactId>` / 取消。
7. 右键 artifact 节点选 `Extract all skills to ▸ <Agent>` 批量导出整个 jar 的 skill。
8. 再次重启 IDE → 安装徽标依然显示，因为 `InstallationRegistryService` 会在启动时重扫 6 个预设目录的 manifest。

详细设计见 `docs/design.md`。

## 致谢

ToolWindow 中用于安装徽标的 5 个 Agent 品牌图标（Claude / Codex / Junie / Cursor / Gemini）来自
[`@lobehub/icons`](https://github.com/lobehub/lobe-icons)（MIT License），仅做了尺寸/无关属性的最小裁剪。
完整版权声明见项目根 `NOTICE` 文件。
