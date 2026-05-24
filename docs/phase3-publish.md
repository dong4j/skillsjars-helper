# 三期 · SkillsJar 发布（需求草案）

> **状态**：规划中 / 未启动开发
> **基线**：本草案以 `docs/design.md` 的「一键发布设计」与 `README.md` 三期表格为底，
> 解决文档间的范围 gap，定义三期的最终交付边界与拆分顺序。
> 启动开发前需要再次确认本草案中「待确认事项」一节。

## 1. 文档间 gap 与本草案的口径

| 来源                            | 三期范围                                                  |
|-------------------------------|-------------------------------------------------------|
| `README.md` 开发状态表             | ① SkillsJar 发布前检查 + 打包配置生成；② Gradle 依赖扫描              |
| `docs/design.md` MVP 步骤 11–14 | ① 发布前检查；② 打包配置；③ Marketplace 搜索安装；④ Skill 生成          |
| 本草案                           | 收敛到 **发布（A 模块）+ Gradle 扫描（B 模块）+ 风险检查轻量版（C 模块）**，其余推迟 |

**剔除项**（明确不在三期）：

- ❌ Marketplace 搜索（design.md 场景 5）— 工作量大、依赖外部 API 调研，独立成四期
- ❌ Skill Generator（design.md 场景 4）— 偏 AI 集成方向，独立成四期
- ❌ 实际上传执行（GitHub Deploy 在线执行 / Maven Central / 私服上传）— 涉及凭据管理风险，留四期

## 2. 三期范围

### A. SkillsJar 发布（核心）

| 编号 | 子模块                                | 说明                                                                                                                 |
|----|------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| A1 | Skills Directory Validator         | 检测 `skills/` 目录结构、`SKILL.md` 校验、frontmatter / allowed-tools / license / 引用文件检查                                     |
| A2 | POM Configuration Generator        | 在 `pom.xml` 中生成 / 更新 `skillsjars-maven-plugin` 配置；自定义 `skillsDir` 时生成对应配置；改动前 diff 预览                              |
| A3 | Local Package Verifier             | 触发 `mvn skillsjars:package`，输出 jar 路径、sha256、构建日志                                                                  |
| A4 | Publish Wizard UI                  | 多步向导，串联 A1→A3，展示坐标 / Skill 列表 / 版本号 / 发布目标 / 校验与打包结果                                                               |
| A5 | `SkillsJarPublisher` 公共 API + 数据模型 | 对齐 `docs/design.md` 第 634–637 行接口草案，定义 `SkillsJarValidationResult` / `PublishOptions` / `PublishPreparationResult` |

### B. Gradle 依赖扫描（顺带）

- 一期已经预留 `dev.dong4j.idea.skillsjars.helper.skillSourceScanner` 扩展点
- `SkillCoordinate` 一期已经支持解析 `Gradle:` 前缀
- 三期只需新增 `GradleLibraryScanner` 实现并在 `plugin.xml` 注册
- 工作量：1 个 scanner 类 + plugin.xml 注册 + 一组测试 fixture

### C. 风险检查轻量版（顺带）

对齐 `docs/design.md` 第 596–608 行的风险表：

| 风险     | 条件                                   |
|--------|--------------------------------------|
| Low    | 只包含 `Read` / `Grep` / `Glob` 等读取类工具  |
| Medium | 包含 `Edit` / `Write` / `MultiEdit`    |
| High   | 包含 `Bash` / 网络命令提示 / 删除文件提示 / 凭据相关文本 |

- 仅在 ToolWindow 现有的 Risk 展示位填上等级（一/二期是空的）
- **不参与**发布阻断；只影响用户决策

## 3. 关键决策点

| 决策点              | 决议                                                                                  | 依据                                                                     |
|------------------|-------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| 是否做实际上传执行        | **否**，仅做 Prep                                                                       | `docs/design.md` 第 546 行原则；涉及凭据管理风险                                    |
| GitHub Deploy 路径 | 仅生成跳转链接（外部浏览器打开），不在 IDE 内执行                                                         | 对齐官网现有 Publish 表单                                                      |
| 校验严格度            | 分级：`name` / `description` / `SKILL.md` 缺失 → **硬阻断**；`license` 缺失 / 引用文件不存在 → **警告** | 对齐 `docs/design.md` 第 502–505 行表格；硬阻断仅针对会让 Maven 打包失败或 SkillsJar 不可用的项 |
| 风险检查是否阻断发布       | **否**，仅展示                                                                           | 风险等级是辅助决策，不应阻断发布                                                       |
| Gradle 是否纳入三期    | **是**                                                                               | 对齐 README 三期表格；工作量小；早做能让一/二期 Maven 专用代码暴露泛化问题                          |

## 4. 数据模型与 API（草案）

`docs/design.md` 第 634–637 行已给出接口骨架，三期落地时按下面对齐：

```java
// 公共 API
public interface SkillsJarPublisher {
    SkillsJarValidationResult validate(@NotNull Project project);
    PublishPreparationResult preparePackage(@NotNull Project project, @NotNull PublishOptions options);
}

// 数据模型 (草案)
public final class SkillsJarValidationResult {
    private final List<SkillValidationIssue> issues;     // name 缺失 / description 缺失 / 引用文件不存在 ...
    private final boolean hasBlockingIssue;              // 是否包含硬阻断项
    // ...
}

public enum SkillValidationSeverity {
    BLOCKING,   // name / description / SKILL.md 缺失
    WARNING     // license / 引用文件 / allowed-tools 解析异常
}

public final class PublishOptions {
    private final BuildTool buildTool;                   // MAVEN (三期) / GRADLE (四期)
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final Path skillsDir;                        // 自定义 skills 目录, null = 默认 skills/
    private final PublishTarget target;                  // GITHUB_DEPLOY / LOCAL_PREP
    // ...
}

public final class PublishPreparationResult {
    private final Path packagedJar;                      // mvn skillsjars:package 输出
    private final String jarSha256;
    private final List<String> nextStepCommands;         // 给用户终端执行的命令提示
    // ...
}
```

API 约束（继承一/二期）：

- 不直接暴露 ToolWindow 组件
- 不要求调用方理解 Jar 扫描细节
- 返回值需要表达错误原因（冲突 / 权限 / 目录缺失）

## 5. 工作量预估

| 模块                              | 量级  | 说明                                 |
|---------------------------------|-----|------------------------------------|
| A1. Skills Directory Validator  | 中   | 校验逻辑 + 错误模型 + i18n                 |
| A2. POM Configuration Generator | 中偏大 | IDEA Maven 模型操作 + diff 预览组件        |
| A3. Local Package Verifier      | 小   | 封装 mvn 调用 + 输出展示                   |
| A4. Publish Wizard UI           | 中   | 多步向导（DialogWrapper）                |
| A5. Publisher API + 数据模型        | 小   | 纯契约层                               |
| B. GradleLibraryScanner         | 小   | 已有扩展点 + Maven scanner 范例           |
| C. 风险检查轻量版                      | 小   | 关键字判断 + ToolWindow Risk 列填值        |
| 文档同步                            | 小   | README / design.md / changelog     |
| **合计**                          | —   | **6–8 个独立 commit 量级**，分 5–7 次小迭代上线 |

## 6. 实施顺序

每一步独立可 `runIde` 验证，互不阻塞：

```
3.1  SkillsJarPublisher API + 数据模型 + skill validator (A5 + A1)
        → 不依赖 UI, 先把契约定下来, 单测覆盖纯逻辑

3.2  POM 配置生成 + diff 预览 (A2)
        → 复用 A1 的校验输出

3.3  本地打包验证 (A3)
        → 复用 A2 的 POM 配置

3.4  Publish Wizard UI 串联 (A4)
        → 串起 A1-A3, 第一个端到端可演示版本

3.5  GradleLibraryScanner (B)
        → 独立模块, 与发布解耦, 可与 3.1-3.4 任一步穿插

3.6  风险检查轻量版 (C)
        → 独立模块, 填上 ToolWindow Risk 列空位

3.7  文档同步 + README 表格更新
        → 收尾, 把 README 「计划中」改为「已完成」
```

## 7. 测试策略

| 模块                        | 测试方式                                            |
|---------------------------|-------------------------------------------------|
| A1 Validator              | 单元测试覆盖各种 SKILL.md 异常场景                          |
| A2 POM Generator          | 端到端 IDEA 集成测试成本高，先靠 `runIde` 手工验证；diff 算法部分可单测  |
| A3 Local Package Verifier | 集成测试需要真实 Maven 环境，靠 `runIde` 手工验证               |
| A4 Wizard UI              | 仅 `runIde` 手工验证                                 |
| B GradleLibraryScanner    | 与 `MavenLibraryScanner` 同样的 fixture jar 测试范式，单测 |
| C 风险检查                    | 单测覆盖各种 allowed-tools 字符串                        |

## 8. 与一/二期的兼容性

- **不破坏现有 API**：三期所有新增 API 都是新增类 / 接口，不修改 `SkillRegistry` / `SkillExportService` 已有签名
- **不破坏现有 UI**：发布功能放在新的 Action / ToolWindow Tab 或新的 Wizard 入口，不动一/二期已有的 ToolWindow 主面板
- **复用 Bundle / Icons**：i18n bundle 与图标资产追加新条目，不动现有条目

## 9. 待确认事项（启动开发前再过一遍）

启动三期开发前需要逐项确认：

1. **三期范围**是否就定为 A（发布 5 子项）+ B（Gradle 扫描）+ C（风险列）？是否需要把 Marketplace 或 Skill Generator 拉进来？
2. **发布上传**第一版是否真的不做？还是希望直接做到能 `mvn deploy`？
3. **校验严格度**分级是否按本草案表格执行？是否有项需要从警告升为阻断？
4. **风险检查**是否真的不阻断发布？
5. **GitHub Deploy** 是否仅做"打开浏览器跳转"？还是要在 IDE 内填表？
6. **测试策略**手工 + 单测的比例是否能接受？还是要补 IDEA 集成测试框架？

## 10. 文档关联

- `docs/design.md` §「一键发布设计」（第 487–546 行）— 设计原则与原始细节
- `docs/design.md` §「核心场景 / 场景 6」（第 156–183 行）— 发布场景的用户视角
- `docs/design.md` §「数据模型草案」（第 245–300 行）— 现有数据模型基底
- `docs/design.md` §「扩展接口」（第 610–645 行）— 公共 API 契约
- `README.md` §「开发状态」表格 — 三期范围声明
