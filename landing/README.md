# SkillsJars Helper — Landing Page

> 这是 SkillsJars Helper 插件的对外 landing page, 由
> [`.claude/skills/landing-page-guide-v2`](../.claude/skills/landing-page-guide-v2/SKILL.md)
> 这份 skill 落地生成. 当前是**静态 HTML 版本**(零构建依赖), 直接 `nginx` 静态托管即可;
> 后续如果想升级成 Next.js + ShadCN UI 版本, 流程见文末"升级路径"一节.

> **部署**: 仓库根有一份 [`deploy.sh`](../deploy.sh), 一键完成 `rsync` 同步 + 可选发布
> 到 Marketplace + 可选上传 zip; 无脑跑 `./deploy.sh -d` 即可只部署 landing 站点.

---

## 目录结构

```
landing/                    # 仓库根下, 与插件主代码并列
├── index.html              # 英文 (默认) 落地页 (含 11 essential elements)
├── zh/
│   └── index.html          # 中文版, 1:1 信息架构, 共享下方 assets/
├── assets/                 # 英文 / 中文双语共用
│   ├── styles.css          # 设计系统 + 全部 section 样式
│   ├── main.js             # 入场动画 / IntersectionObserver / hover 交互
│   ├── plugin-icon.svg     # 插件官方图标 (Marketplace 同款), header / footer / favicon 都引这一份
│   ├── banner.png          # OG image (复用项目根 banner.png)
│   └── agents/             # 9 个 Agent 高清品牌徽标 (复用 src/main/resources)
├── skillsjars-helper.dong4j.site.conf
│                           # 软链 → ~/Developer/3.Knowledge/Site/hexo/dependencies/ecs/aliyun/nginx/conf.d/skillsjars-helper.dong4j.site.conf
│                           # (ECS 所有 nginx 配置集中管理, 本地软链名 = 中央文件名 = 远程文件名 三处统一,
│                           #  在本仓库里改 == 改中央文件)
└── README.md               # 当前文件
```

URL 设计:

- `/` → 英文版 (`index.html`)
- `/zh/` → 中文版 (`zh/index.html`)
- 顶部右侧 `EN | 中文` toggle 互相跳转, 当前语言用暖橙高亮
- 两个版本都通过 `<link rel="alternate" hreflang="…">` 互相引用,
  方便搜索引擎按访问者语言匹配

零依赖原则:

- **不需要** `node`, `npm`, `pnpm`, `next`, `tailwind` 任何前端工具链.
- **唯一外网依赖**是 Google Fonts (Inter + JetBrains Mono),
  已用 `display=swap` 防 FOIT, 内网部署时见"离线字体"小节.
- 体积 < 110 KB (含 9 个图标 + 双语两份 HTML), 加载时间几乎只取决于网络往返.

---

## 本地预览

任意一个静态文件服务器都能跑. 推荐 Python:

```bash
cd landing
python3 -m http.server 8000
# 然后浏览器打开 http://localhost:8000
```

或用 `npx serve` / `caddy` / `darkhttpd` 都行.

---

## 部署到云服务器 (推荐: 用根目录 deploy.sh)

仓库根的 `deploy.sh` 已经把 `rsync` / `nginx` 配置部署 / 插件市场发布全部封装好.
**先在脚本顶部 CONFIG 区把 `REMOTE_HOST` / `REMOTE_ROOT_DIR` / `SITE_URL` 改成你自己的值**,
然后一行命令搞定:

```bash
./deploy.sh -d         # 仅部署 landing 站点 (最常用)
./deploy.sh -n         # 仅部署 nginx 配置 + reload (首次部署需要)
./deploy.sh            # 默认: publish + upload zip + 部署 landing
./deploy.sh -h         # 看完整 usage
```

完整原理见 [`deploy.sh`](../deploy.sh) 脚本顶部注释.

### 手工部署 (备选)

如果不想用脚本, 也可以直接 rsync:

```bash
# 服务器上的目标目录 (与 deploy.sh REMOTE_ROOT_DIR 一致)
rsync -avz --delete --chmod=F644,D755 \
  --exclude 'skillsjars-helper.dong4j.site.conf' --exclude 'README.md' \
  landing/ user@your-server:/var/www/skillsjars-helper/

# nginx 配置: landing/skillsjars-helper.dong4j.site.conf 是软链, --copy-links 把它解析后上传
rsync -avz --copy-links landing/skillsjars-helper.dong4j.site.conf \
  user@your-server:/etc/nginx/conf.d/skillsjars-helper.dong4j.site.conf
ssh user@your-server "nginx -t && systemctl reload nginx"
```

### nginx 配置的中央管理 (重要)

`landing/skillsjars-helper.dong4j.site.conf` 是软链, 指向作者本机 nginx 配置仓库:

```
~/Developer/3.Knowledge/Site/hexo/dependencies/ecs/aliyun/nginx/conf.d/skillsjars-helper.dong4j.site.conf
```

命名约定: **本地软链名 = 中央文件名 = 远程文件名**三处统一, 不再用 `nginx.conf` 这种通名,
一眼就能在 `landing/` 里看出对应的是哪个站点。

含义:

- 在本仓库内**编辑 `landing/skillsjars-helper.dong4j.site.conf`** 等价于直接改中央配置文件,
  方便插件作者在 plugin 上下文里调 nginx.
- 部署有两条等价路径:
  1. **本仓库**`./deploy.sh -n` —— 解析软链后只推这一份 conf, 适合迭代时快速 reload.
  2. **中央仓库**`deploy.sh` (即 `ecs/aliyun/nginx/deploy.sh`) —— 整套 conf.d 全量同步,
     用于跨站点的批量发布.
- 三方 fork 者 (没有作者本机那套目录) 看到的就是一个**断链**;
  此时把软链删掉, 在原地放回任何普通 conf 文件 (改 `NGINX_CONF_NAME` 变量也行),
  deploy.sh 全程兼容.

### 加 HTTPS (推荐 certbot)

```bash
sudo certbot --nginx -d skillsjars-helper.dong4j.site
```

### 验证

```bash
curl -I https://skillsjars-helper.dong4j.site/
# 期望: HTTP/2 200, Cache-Control 头按 skillsjars-helper.dong4j.site.conf 配置返回
```

---

## 替换占位文案 / 链接

`index.html` 与 `zh/index.html` 内目前所有链接都已指向真实地址:

- **GitHub**: <https://github.com/dong4j/skillsjars-helper>
- **Marketplace 插件页**: <https://plugins.jetbrains.com/plugin/31935-skillsjars-helper>
- **作者其他插件 (vendor 页)**: <https://plugins.jetbrains.com/vendor/9afaba35-91ea-4364-8ced-64db868dd23e>
- **作者邮箱**: `dong4j@gmail.com`

只需要根据实际情况调整的部分:

1. **域名**: 决定使用哪个域名后, 可在 `<meta property="og:image">` 等处补全绝对 URL,
   并在 `og:url` / `canonical` 里加上.
2. **真实 testimonials**: 当前 `Use cases` / `适用场景` 区是 4 类典型受众的真实场景, 而非
   假人名假头像 (符合 user rules 不造假). 如果以后收集到真实用户引用,
   可以再加一个 "What users say" / "用户怎么说" 区.

---

## 字体策略

当前用 **Inter (display + body 同家族多重) + JetBrains Mono (code)**, 中文 fallback
依次走 PingFang SC → Microsoft YaHei → Hiragino Sans GB → system-ui. 中文不引入
任何 Web Font, 直接用系统字体, 加载零成本且效果稳定.

为避免国内 Google Fonts 偶尔被墙, 有两条退路:

### 选项 A: 改用国内镜像

把 `index.html` 与 `zh/index.html` 头部的
`https://fonts.googleapis.com/css2?...` 替换为
`https://fonts.font.im/css2?...` 或 `https://fonts.loli.net/css2?...`.

### 选项 B: 完全离线 (推荐生产环境)

1. 用 [google-webfonts-helper](https://gwfh.mranftl.com/fonts) 下载
   Inter / JetBrains Mono 的 woff2 文件到 `assets/fonts/`.
2. 把两个 `index.html` 中 `<link>` 引用的 Google Fonts 替换为本地 `@font-face`,
   可以追加在 `assets/styles.css` 顶部, 例如:

   ```css
   @font-face {
     font-family: 'Inter';
     font-style: normal;
     font-weight: 400 700;
     font-display: swap;
     src: url('./fonts/inter.woff2') format('woff2');
   }
   ```

3. 删除两个 `index.html` 里 Google Fonts 的 `<link>` 标签.

字体文件总计约 60–90 KB (gzip 后), 离线后页面真正变成"零外部依赖".

---

## 11 essential elements 自检表

按 `landing-page-guide-v2` 的验证清单一一勾过:

- [x] URL with keywords — `index.html` 在 `<title>` / `<meta>` / 文案中嵌入关键词,域名建议含 `skillsjars-helper`.
- [x] Company logo (header) — sticky header + 暖橙渐变方块 + Brand 名 / 版本.
- [x] SEO-optimized title and subtitle — `H1` 5.4rem clamp + 副标 + 高亮渐变 `<em>`.
- [x] Primary CTA (hero) — Install on Marketplace + Star on GitHub 双 CTA.
- [x] Social proof (hero) — 3 stat (9 / 0 / MIT) + Agents matrix 视觉化代理覆盖.
- [x] Images / videos (media) — IDE Tool Window mock-up (HTML/CSS 实做, 含真实
  agent 图标徽标), 比放空截图更专业.
- [x] Core benefits / features — 6 个 bento card, 不对称布局, hover cursor follow.
- [x] Customer testimonials — 替换为 "Who it's for" 4 类受众真实场景
  (项目暂无真实推荐, **不编造假人名假头像**).
- [x] FAQ section — 8 真实问题, 原生 `<details>` accordion.
- [x] Final CTA — 暖橙渐变全宽卡片, 双 CTA + 4 项元数据.
- [x] Footer — 4 列 (Product / Resources / Author / Brand) + 底部版权.

设计原则自检 (来自 `landing-page-guide-v2` "AVOID Generic AI Aesthetics"):

- [~] 字体: 现用 Inter — **此条违反 skill 字体禁令**, 但应用户明确反馈
  "Unbounded / Manrope 字体看着别扭, 改大众点", 用户偏好优先于 skill 推荐.
  中文 fallback 走系统字体 (PingFang SC / 雅黑), 整体观感更舒适.
- [x] 不用 "purple gradient on white" — 改深色基底 + 暖橙渐变.
- [x] 不用纯白背景 — 噪点 SVG + 双 radial gradient 增加质感.
- [x] 不用对称 3 列 grid — bento 6 卡 + 不对称 hero (1.3fr / 1fr).
- [x] ShadCN 默认按钮没有出现 — 全部 button 都是自定义 pill + 暖橙阴影.

双语:

- [x] 英文 / 中文双版本, 顶部右上 toggle 切换
- [x] 中文版用 `lang="zh-Hans"` + `og:locale=zh_CN`, hreflang 双向声明
- [x] 中文不另引 Web Font, 直接用系统字体保证可读性

---

## 升级路径: 静态 HTML → Next.js 14 + ShadCN UI

如果以后想严格按 skill 默认要求重写成 Next.js 工程:

1. 在仓库外 `~/Developer/0.Worker/opensource/idea/skillsjars-helper-website/`
   `npx create-next-app@latest --typescript --tailwind --app --src-dir`.
2. `npx shadcn-ui@latest add button card accordion badge avatar separator input`.
3. 把当前 `styles.css` 的 CSS variables 拷到 `app/globals.css`.
4. 按 `landing-page-guide-v2/references/component-examples.md` 的样板组件,
   保留当前文案 / 设计语言, 把每个 section 改成 React 组件.
5. `Vercel` 一键部署 (或继续 nginx 反代 `next start`).

当前静态版可作为**视觉基线**: 任何 Next.js 重写如果在 aesthetic / 信息架构上和它
偏离太远, 就要回头确认是否丢掉了原本想表达的设计意图.

---

## 维护提示

- 修改 9 个 Agent 列表? 同时改 `index.html` 中 `agents-grid` 区
    + `src/main/resources/icons/agents/` 真实图标 + `assets/agents/` 拷贝.
- 改设计 token (颜色 / 字体)? 集中在 `styles.css` 顶部 `:root { ... }` 一处.
- 加新 section? 沿用现有 pattern: `<section>` + `.section-head` + `.reveal-stagger`,
  动效会自动接管.
- 插件图标更新 (`src/main/resources/META-INF/pluginIcon.svg`)? 重新拷贝到
  `landing/assets/plugin-icon.svg`, header / footer / favicon 三处自动跟随.
- 第三方插件集成指南目前指向 [`docs/extension-points.md`](../docs/extension-points.md)
  (扩展点接入文档, 详细列举了 `SkillRegistry` / `SkillExportService` /
  `skillSourceScanner` 等对外 API). 文档结构稳定, 改名前先评估对外影响.
  如要改链接, 更新:
  (1) benefit-5 卡片底部 `.benefit-link` 的 href
  (2) Footer Resources / 资源 列下的 "Plugin integration guide / 第三方插件集成指南" href
  双语两份文件都要改 (4 处链接).
