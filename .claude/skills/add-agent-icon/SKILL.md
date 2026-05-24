---
name: add-agent-icon
description: 把 Agent 品牌原图加工成 16/32 px HiDPI 一对 PNG 资产
---

# /add-agent-icon - 加工 Agent 品牌徽标

把用户提供的高分辨率透明 PNG 加工成 IDEA Tool Window 用的 16×16 + 32×32@2x
HiDPI 一对资产, 视觉风格与其他 Agent 徽标对齐 (无明显高低差).

> 这个 skill 通常被 [add-agent](../add-agent/SKILL.md) 调用, 但单独存在的场景
> 也常见 — 比如某个 Agent 换 logo, 或想统一刷一遍所有徽标的视觉权重.

## Usage

```
/add-agent-icon [agent-id] [source-png-path]
```

- `agent-id` — 输出文件名前缀, 例 `qoder`
- `source-png-path` — 原图绝对路径

## Information Needed

1. **Agent ID** — 必须与 `SkillTargetDirectory.AGENT_*` 常量一致 (本项目里全部小写)
2. **原图绝对路径** — 透明背景 PNG, 分辨率 ≥ 256×256, **推荐 640×640**;
   原图自带的边距越宽, 加工时 alpha bbox 裁剪越能拉齐视觉权重
3. **不要修改原图**. 用户通常单独维护原图收藏 (例 `~/Developer/1.AI/ai-logo/`),
   后续可能复用到其他项目, 该目录视为只读

## Workflow

### 1. 准备 Pillow

需要 Pillow (PIL fork) 在 Python venv 里. macOS 的 system Python 有 PEP 668
保护, 不能直接 `pip install`, 必须 venv:

```bash
if [ ! -x /tmp/skillsjars-pillow/bin/python ]; then
  python3 -m venv /tmp/skillsjars-pillow && \
  /tmp/skillsjars-pillow/bin/pip install --quiet Pillow
fi
PY=/tmp/skillsjars-pillow/bin/python
```

### 2. 加工脚本

把下面这段保存到 `/tmp/process_icon.py` 或临时文件即可, **不要 commit 到仓库**
(本仓库不沉淀加工脚本, skill 文档自身就是可复制的 spec):

```python
"""把单张高分辨率透明 PNG 加工成 16x16 + 32x32 一对 HiDPI 资产.

为什么 alpha bbox 裁剪 + content size 留 padding 的双步骤:
- 不同来源的 logo 自带的透明边距宽窄不一, 直接缩放到 16 会出现"有的看着大,
  有的看着小"的视觉错位 (徽标连排时尤其明显).
- 先按 alpha 通道裁出真正内容的 bbox, 再缩到 14x14 (1x) / 28x28 (@2x), 居中
  到 16/32 透明画布, 即可拉齐视觉权重, 同时留出 1/2 px 的视觉呼吸空间.
"""
from PIL import Image
from pathlib import Path
import sys

src = Path(sys.argv[1])              # 原图绝对路径
dst_dir = Path(sys.argv[2])          # 输出目录, 通常 src/main/resources/icons/agents
agent_id = sys.argv[3]               # 例 "qoder"

img = Image.open(src).convert("RGBA")

# 1. 按 alpha 通道裁出真正内容 bbox, 抹平不同原图的 padding 差异
bbox = img.getbbox()
if bbox is None:
    raise SystemExit(f"original image has no opaque pixels: {src}")
img = img.crop(bbox)


def fit_into(canvas_size: int, content_size: int) -> Image.Image:
    """缩放到 content_size 内 (保持纵横比), 居中放到 canvas_size 透明画布上."""
    w, h = img.size
    if w >= h:
        nw, nh = content_size, max(1, round(h * content_size / w))
    else:
        nw, nh = max(1, round(w * content_size / h)), content_size
    scaled = img.resize((nw, nh), Image.LANCZOS)
    bg = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bg.paste(scaled, ((canvas_size - nw) // 2, (canvas_size - nh) // 2), scaled)
    return bg


# 1x: 16×16 画布, 内容 14×14, 留 1px 透明边
fit_into(16, 14).save(dst_dir / f"{agent_id}.png")

# @2x: 32×32 画布, 内容 28×28, 留 2px 透明边
fit_into(32, 28).save(dst_dir / f"{agent_id}@2x.png")

print(f"OK: {agent_id}.png + {agent_id}@2x.png")
```

### 3. 调用

```bash
$PY /tmp/process_icon.py \
    ~/Developer/1.AI/ai-logo/qoder.png \
    src/main/resources/icons/agents \
    qoder
```

预期输出:

```
src/main/resources/icons/agents/qoder.png      # 16x16
src/main/resources/icons/agents/qoder@2x.png   # 32x32
```

### 4. HiDPI 命名约定

- `{agentId}.png` — 16×16, 1x 屏 / 100% 缩放使用
- `{agentId}@2x.png` — 32×32, Retina / 200% 缩放使用
- IDEA 的 `IconLoader.getIcon(...)` 只需指向 `.png`, **`@2x` 会被自动按命名解析**,
  Java 代码里不要分别 load 两个文件

## Visual Acceptance

启动 IDE (`./gradlew runIde`) 后对比新徽标与已有徽标:

- **视觉重量大致一致** — 这是 alpha bbox 裁剪 + 14px content 的核心目标,
  徽标连排时不应有明显大小差
- **透明背景** — 浅色 / 深色主题切换时不应有白色 / 灰色底块出现
- **选中态高亮叠加无明显锯齿** — 如果出现, 通常是原图本身有半透明边缘羽化,
  和 IDEA 的选中色叠加后边缘看起来发毛, 此时换张更干净的原图

## Don'ts

- **不要修改 `~/Developer/1.AI/ai-logo/` 下的原图** — 用户视为只读资产库
- **不要把彩色品牌色单色化** — 品牌色本身就是识别度, 强行染主题色反而失真;
  本项目所有徽标都用品牌彩色, 不参与 IDEA 主题反相
- **不要用 SVG** — 本项目历史用过 SVG, 已统一切到 PNG, 见 `git show bb06589`;
  新徽标必须 PNG, 不要再退回 SVG
- **不要把加工脚本 commit 到仓库** — 它是一次性工具, skill 文档本身就是 spec

## Canonical Example

`commit bb06589` — SVG → PNG 整体迁移时, 9 个 Agent 徽标 (claude / codex / junie /
cursor / gemini / qoder / trae / codebuddy / agents) 是同一脚本批量加工的,
视觉一致性可参考该 commit 的资产作为基线.

## Key Constants in Script

- `canvas_size=16, content_size=14` — 1x 资产, 留 1px margin
- `canvas_size=32, content_size=28` — @2x 资产, 留 2px margin
- `Image.LANCZOS` — 缩放算法, 兼顾锐度与平滑, 比 BILINEAR 视觉质量好

如果整批徽标看起来都偏小, 把 `content_size` 调到 15/30; 偏大则调到 13/26.
**不建议** 单独给某个 Agent 用不同的 content_size, 那是治标不治本 — 应该回头
检查那个 Agent 原图的 alpha bbox 是不是不规则.
