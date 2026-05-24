---
name: add-agent
description: 接入一个新的 Agent 工具支持 (新增预设目录 + 品牌徽标 + 文档同步)
---

# /add-agent - 接入新 Agent

为 SkillsJars Helper 加一个新的 Agent 工具支持. 完成后:

- Tool Window 右键菜单 "Extract to ▸" 出现新条目 `.{agentId}/skills`
- 已安装的 skill 在树叶子右侧显示该 Agent 的品牌徽标
- 用户在 IDE 中可以一键导出 skill 到该 Agent 的工作目录

## Usage

```
/add-agent [agent-id]
```

- `agent-id` — 全小写 ASCII 单词, 也是项目根下隐藏目录名 (去掉前导 ".") .
  例: `qoder` → 磁盘目录 `.qoder/skills/`

## Information Needed

1. **Agent ID** — 全小写, 例 `qoder` / `trae` / `codebuddy`
2. **Display Name** — 用于 Javadoc 注释 / 文档展示, 例 `Qoder (阿里)` / `CodeBuddy (腾讯)`
3. **目录约定是否符合 `.{id}/skills`** — 截至当前所有官方 Agent 都符合;
   不符合时需要扩展 `SkillTargetDirectory.presetDirName()`, 属于例外, 单独讨论
4. **品牌图标原图** — 透明背景 PNG, 分辨率 ≥ 256×256 (推荐 640×640).
   交给 [add-agent-icon](../add-agent-icon/SKILL.md) skill 加工成 16/32 PNG

## Files to Modify

### 1. 注册 Agent ID — `src/main/java/dev/dong4j/idea/skillsjars/helper/api/model/SkillTargetDirectory.java`

新增常量 + 加入 `PRESET_AGENT_IDS` 末尾 (列表顺序就是菜单顺序):

```java
/** {Display Name} — Skill 目录约定 .{agentId}/skills/{name}/SKILL.md, 同时支持用户级 ~/.{agentId}/skills. */
public static final String AGENT_{AGENT_ID_UPPER} = "{agentId}";

public static final List<String> PRESET_AGENT_IDS = List.of(
    AGENT_CLAUDE,
    AGENT_CODEX,
    // ... 已有 ...
    AGENT_{AGENT_ID_UPPER}    // ← 加在末尾
);
```

> **零外溢**: 加完后 `TargetDirectoryDetector` / `InstallationRegistryService` /
> `SkillExportService` / `SkillsToolWindowPanel` 等都是从 `PRESET_AGENT_IDS`
> 数据驱动消费的, **不需要改一行代码**. 如果你发现某处 detector 还要改,
> 说明你的目录命名约定不属于 `.{id}/skills` 标准格式 — 需要单独评估.

### 2. 注册图标 — `src/main/java/icons/SkillsJarsHelperIcons.java`

加 field + 在 `forAgent()` switch 加 case (Java switch 没法基于反射枚举常量, 必须显式列):

```java
/** {Display Name}. */
public static final Icon AGENT_{AGENT_ID_UPPER} = load("/icons/agents/{agentId}.png");

@Nullable
public static Icon forAgent(@NotNull String agentId) {
    return switch (agentId) {
        // ... 已有 case ...
        case SkillTargetDirectory.AGENT_{AGENT_ID_UPPER} -> AGENT_{AGENT_ID_UPPER};
        // ... 已有 ...
    };
}
```

### 3. 加工图标资产

调用 [add-agent-icon](../add-agent-icon/SKILL.md) skill, 产出:

- `src/main/resources/icons/agents/{agentId}.png` (16×16)
- `src/main/resources/icons/agents/{agentId}@2x.png` (32×32, HiDPI)

### 4. 同步文档

| 文件                            | 改什么                                      |
|-------------------------------|------------------------------------------|
| `docs/design.md`              | "支持的 Agent 列表" 一节加一项 `.{agentId}/skills` |
| `README.md`                   | 项目简介里的 Agent 列表                          |
| `includes/pluginChanges.html` | changelog (中英两段都要补, 描述本次新增了什么 Agent)     |

## Build & Test

```bash
./gradlew compileJava                       # 编译验证
./gradlew runIde                            # 启动沙箱 IDEA
```

视觉验收 (在沙箱 IDE 中):

- Tool Window 右键菜单 "Extract to ▸" 出现新条目 `.{agentId}/skills`
- 把任意 skill 导出到该目录后, 树叶子右侧出现新的品牌徽标
- 徽标视觉大小与其他 Agent 一致 (16×16, 不应明显偏大或偏小, 这取决于
  add-agent-icon 是否做了 alpha bbox 裁剪)

## Canonical Example

最近一次完整新增是接入 CodeBuddy (腾讯), 见 `git show a2d30f0`.
该 commit 的特点: **detector 与所有 javadoc 零改动**, 仅改了:

- `SkillTargetDirectory.java` (常量段 + PRESET_AGENT_IDS)
- `SkillsJarsHelperIcons.java` (field + forAgent switch)
- 1 个图标资产 (彼时还是 svg, 现在是 png + @2x)
- 4 份文档

完整改动面 ≤ 10 行 Java + 资产 + 文档. 如果你的改动远超这个量级, 大概率走偏了.

## Pitfalls

- **`PRESET_AGENT_IDS` 的列表顺序就是 UI 菜单顺序**. 不要随手插中间, 除非
  确实想调整菜单顺序.
- **`forAgent()` 必须手动加 case**. 忘了加, 徽标会显示成 IDEA 默认 Folder 图标
  (因为 `null` 走 renderer 的 fallback 分支), 看上去像 bug 但其实是漏注册.
- **目录名只能是小写 ASCII**. `presetDirName()` 默认拼 `"." + agentId`,
  大写会变成 `.MyAgent` 这种不符合 Unix 隐藏目录约定的怪名字.
- **不要在 i18n bundle 里给每个 Agent 加 displayName key**. 当前实现是直接用
  `SkillTargetDirectory.getDisplayName()` (即 `.{agentId}/skills`) 作为菜单文案,
  保持简洁, 不引入翻译维护成本.

## Key Files

- `src/main/java/dev/dong4j/idea/skillsjars/helper/api/model/SkillTargetDirectory.java`
  — 预设清单的单一权威源 (`PRESET_AGENT_IDS`) 与命名约定 (`presetDirName`)
- `src/main/java/dev/dong4j/idea/skillsjars/helper/export/TargetDirectoryDetector.java`
  — 数据驱动的探测器, 加新 Agent 时**应该**零改动
- `src/main/java/icons/SkillsJarsHelperIcons.java`
  — 图标注册表与 `forAgent()` 路由
- `src/main/resources/icons/agents/`
  — Agent 品牌徽标 PNG 资产目录
