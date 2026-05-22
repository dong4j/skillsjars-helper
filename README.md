# SkillsJars Helper

SkillsJars Helper 是一个面向 IntelliJ IDEA 的插件，目标是让使用 [skillsjars](https://github.com/dong4j/skillsjars) 打包的 `skill.md` 能够在 IDE 内被发现、查看、导出和复用。

这个项目来自一次关于 SkillsJars 使用方式的讨论：当 `skill.md` 和相关资源被打进 Java 依赖 Jar 后，IDEA 应该能够像管理普通依赖一样管理这些 Skill，而不是让开发者手工去翻 classpath、解压 Jar 或记忆文件路径。

## 目标

- 在 IDEA 中扫描当前项目 classpath，发现包含 Skill 定义的 Jar。
- 以 Tool Window 的形式展示 Skill 列表，包含名称、描述、来源 Jar 和资源位置。
- 将 Jar 内的 `skill.md` 及其相关文件导出到指定 Skill 目录，方便被 Codex、Claude Code 或其他 Agent 工具直接使用。
- 为当前 Java 模块生成 `skill.md`，降低使用 skillsjars 打包 Skill 的成本。
- 提供标准 IDEA Plugin 扩展接口，让其他插件可以复用 Skill 检索能力。

## 核心功能规划

### 1. Skill 列表展示

插件会扫描当前项目可见的 classpath，识别包含 Skill 资源的 Jar，并在 Tool Window 中以列表形式展示。

每个 Skill 条目至少包含：

- Skill 名称
- Skill 描述
- 来源 Jar
- `skill.md` 在 Jar 内的路径
- 可选的关联资源文件

这个视图的重点是快速回答两个问题：当前项目带来了哪些 Skill，以及这些 Skill 来自哪个依赖。

### 2. Skill 导出

插件支持将 Jar 内的 `skill.md` 和相关文件导出到用户指定的 Skill 目录。

后续可以增加本机 Agent 工具目录自动检测，例如：

- Codex Skill 目录
- Claude Code Skill 目录
- 用户自定义 Skill 目录

自动检测只负责提供候选目录，实际导出目标仍应由用户确认，避免误写全局工具目录。

### 3. Skill 生成

插件可以为当前选中的 Java 模块生成 `skill.md`，用于后续通过 skillsjars 打包发布。

生成能力分为两层：

- 基础生成：根据模块名、包结构、README、源码入口等信息生成初始模板。
- AI 增强：可选集成 IntelliAI Engine 插件，根据项目上下文生成更完整的 Skill 描述、触发规则和使用边界。

AI 能力不是插件的强依赖。没有 IntelliAI Engine 时，插件仍应保留基础模板生成能力。

### 4. 扩展接口

插件需要暴露一个稳定的 IDEA Plugin 扩展接口，供其他插件复用 Skill 检索能力。

预期接口能力包括：

- 查询当前项目可用的 Skill 列表
- 根据 Skill 名称或来源 Jar 查找 Skill
- 获取 `skill.md` 内容和关联资源
- 监听 Skill 扫描结果变化

扩展接口应优先返回结构化模型，而不是直接暴露 UI 或文件系统细节，方便其他插件在自己的业务场景中复用。

## 初步架构设想

```text
IDEA Project
  |
  |-- Classpath Scanner
  |     `-- 查找包含 skill.md 的 Jar
  |
  |-- Skill Index
  |     `-- 缓存 Skill 元数据和来源信息
  |
  |-- Tool Window
  |     `-- 展示 Skill 列表、详情和导出入口
  |
  |-- Export Service
  |     `-- 导出 skill.md 与相关资源到目标目录
  |
  |-- Generator Service
  |     `-- 为当前模块生成 skill.md
  |
  `-- Extension API
        `-- 向其他 IDEA 插件暴露 Skill 检索能力
```

## 非目标

- 不直接替代 skillsjars 的打包能力。
- 不强制绑定某一个 Agent 工具。
- 不在没有用户确认的情况下写入全局 Skill 目录。
- 不把 AI 生成功能作为插件启动或扫描能力的前置依赖。

## 开发状态

当前仓库处于初始化阶段，README 先固定产品边界和功能方向。后续实现会优先从 classpath 扫描、Skill 元数据模型和 Tool Window 列表开始。

