#!/bin/bash
#
# SkillsJars Helper — 一键发布 / 部署脚本
#
# 用法 (默认 = -p + -z + -d 三件套):
#   ./deploy.sh                # 全做: 发布到 Marketplace + 上传 zip + 部署 landing
#   ./deploy.sh -d             # 仅部署 landing 站点 (最常用)
#   ./deploy.sh -p             # 仅发布到 Marketplace (publishDefault)
#   ./deploy.sh -P             # 发布到 beta 通道 (publishBeta)
#   ./deploy.sh -z             # 仅打 zip + 上传到下载站
#   ./deploy.sh -n             # 仅部署 nginx 配置 + 远程 reload
#   ./deploy.sh -v 2026.1.1001 # 先改 gradle.properties 里的 pluginVersion 再走默认流程
#   ./deploy.sh -h             # 显示完整 usage
#
# 设计参考: zeka-idea-plugin/deploy.sh (多插件 monorepo 版本), 已简化为单插件版本.

set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

############################################
# CONFIG — 第一次部署前先把这块改成自己的值
############################################
# 远程主机. 推荐在 ~/.ssh/config 里配置 SSH 别名 (Host aliyun ... HostName ...);
# 没配的话可改成 user@1.2.3.4 这种完整形式.
REMOTE_HOST="${SKILLSJARS_DEPLOY_HOST:-aliyun}"

# 远程站点 root 目录, 必须与 landing/skillsjars-helper.dong4j.site.conf 中 `root` 一致.
REMOTE_ROOT_DIR="${SKILLSJARS_DEPLOY_ROOT:-/var/www/skillsjars-helper}"

# 远程 nginx conf.d 目录 (一般不用改).
REMOTE_NGINX_CONF_DIR="${SKILLSJARS_DEPLOY_NGINX_DIR:-/etc/nginx/conf.d}"

# 远程下载文件服务器目录 (用于 -z 把 zip 推到独立的下载站; 不需要可留空).
REMOTE_DATA_DIR="${SKILLSJARS_DEPLOY_DATA_DIR:-}"

# 部署完成后输出的访问地址 (仅文案展示, 不影响实际部署逻辑).
SITE_URL="${SKILLSJARS_DEPLOY_SITE_URL:-https://skillsjars-helper.dong4j.site}"

# Marketplace plugin ID (用于发布完成后输出 Marketplace 链接).
PLUGIN_MARKETPLACE_ID="31935"

############################################
# 派生路径 — 一般不用改
############################################
PLUGIN_NAME="skillsjars-helper"
LANDING_DIR="$SCRIPT_DIR/landing"
# 站点 nginx 配置文件 (本地路径).
# 它是一个软链 → /Users/.../ecs/aliyun/nginx/conf.d/skillsjars-helper.dong4j.site.conf
# (中央 nginx 统一管理), 在仓库内编辑等价于直接改中央文件;
# 文件名与中央 conf.d/ 内的命名约定保持一致, 一眼可看出对应哪个站点;
# 上传时用 rsync --copy-links 把软链解析成实际内容推上去.
NGINX_CONF_NAME="skillsjars-helper.dong4j.site.conf"
NGINX_CONF_FILE="$LANDING_DIR/$NGINX_CONF_NAME"
ZIP_DIR="$SCRIPT_DIR/build/distributions"
GRADLE_PROPERTIES="$SCRIPT_DIR/gradle.properties"
# 远程文件名: 默认与本地 NGINX_CONF_NAME 同名 (符合中央管理约定),
# 仅在需要把同一份配置部署成另一个文件名时才需要覆盖.
NGINX_REMOTE_CONF_NAME="${SKILLSJARS_DEPLOY_NGINX_CONF_NAME:-$NGINX_CONF_NAME}"

############################################
# usage helper
############################################
print_usage() {
  cat <<'EOF'
SkillsJars Helper 部署脚本

用法:
  ./deploy.sh [options]

选项 (不传任何参数 = -p + -z + -d 全做):
  -d              仅部署 landing 站点 (rsync landing/ → REMOTE_ROOT_DIR/)
  -p              仅发布到 JetBrains Marketplace 默认通道 (publishDefault)
  -P              仅发布到 Marketplace beta 通道 (publishBeta)
  -z              仅打 zip + 上传到下载站 (REMOTE_ROOT_DIR + 可选 REMOTE_DATA_DIR)
  -n              仅部署 nginx 配置 (landing/skillsjars-helper.dong4j.site.conf) 并远程 reload
  -v <version>    先把 gradle.properties 中 pluginVersion 改为此值, 再继续后续步骤
  -h              显示本帮助

环境变量覆盖 (临时改一次部署目标特别有用, 不用改脚本):
  SKILLSJARS_DEPLOY_HOST            覆盖 REMOTE_HOST    (默认 aliyun)
  SKILLSJARS_DEPLOY_ROOT            覆盖 REMOTE_ROOT_DIR (默认 /var/www/skillsjars-helper)
  SKILLSJARS_DEPLOY_NGINX_DIR       覆盖远程 nginx conf.d 目录 (默认 /etc/nginx/conf.d)
  SKILLSJARS_DEPLOY_NGINX_CONF_NAME 覆盖远程 nginx 文件名 (默认 skillsjars-helper.dong4j.site.conf)
  SKILLSJARS_DEPLOY_DATA_DIR        覆盖独立下载站目录 (默认空 = 跳过)
  SKILLSJARS_DEPLOY_SITE_URL        覆盖收尾输出的访问地址 (默认 https://skillsjars-helper.dong4j.site)

nginx 配置说明:
  landing/skillsjars-helper.dong4j.site.conf 是软链 → ~/Developer/3.Knowledge/Site/hexo/dependencies/ecs/aliyun/nginx/conf.d/skillsjars-helper.dong4j.site.conf
  (ECS 所有 nginx 配置集中管理, 本地软链名 = 中央文件名 = 远程文件名 三处统一).
  在本仓库内编辑即等价于改中央配置文件;
  -n 选项用 rsync --copy-links 把软链解析后推到远程并 reload.
  如需走中央 deploy.sh 批量同步, 直接在中央仓库执行那份脚本即可.

示例:
  SKILLSJARS_DEPLOY_HOST=staging ./deploy.sh -d   # 临时部署到 staging 主机
  ./deploy.sh -v 2026.1.1001                      # 升版本 + 默认全套
EOF
}

############################################
# 参数解析
############################################
do_publish=false
do_publish_beta=false
do_zip=false
do_site=false
do_nginx=false
explicit_action=false
VERSION=""

while getopts ":v:zdpPnh" opt; do
  case $opt in
    v)
      VERSION="$OPTARG"
      ;;
    z)
      do_zip=true
      explicit_action=true
      ;;
    d)
      do_site=true
      explicit_action=true
      ;;
    p)
      do_publish=true
      explicit_action=true
      ;;
    P)
      do_publish_beta=true
      explicit_action=true
      ;;
    n)
      do_nginx=true
      explicit_action=true
      ;;
    h)
      print_usage
      exit 0
      ;;
    \?)
      echo "错误: 未知选项 -$OPTARG" >&2
      print_usage
      exit 1
      ;;
    :)
      echo "错误: 选项 -$OPTARG 需要参数" >&2
      print_usage
      exit 1
      ;;
  esac
done

# 默认 (无 explicit action) = 发布 + 上传 zip + 部署 landing
if ! $explicit_action; then
  do_publish=true
  do_zip=true
  do_site=true
fi

############################################
# 概览输出
############################################
echo "================================"
echo "SkillsJars Helper 部署"
echo "================================"
echo "  远程主机:     $REMOTE_HOST"
echo "  远程站点根:    $REMOTE_ROOT_DIR"
echo "  访问地址:     $SITE_URL"
echo "--------------------------------"
echo "执行计划:"
$do_publish      && echo "  ✓ publishDefault → JetBrains Marketplace"
$do_publish_beta && echo "  ✓ publishBeta   → Marketplace beta 通道"
$do_zip          && echo "  ✓ 上传 zip → $REMOTE_HOST:$REMOTE_ROOT_DIR/"
$do_site         && echo "  ✓ rsync landing/ → $REMOTE_HOST:$REMOTE_ROOT_DIR/"
$do_nginx        && echo "  ✓ 上传 $NGINX_CONF_NAME 并 reload"
[ -n "$VERSION" ] && echo "  ✓ 改 pluginVersion → $VERSION"
echo "================================"
echo ""

############################################
# 0) 可选: 先改版本号
############################################
if [ -n "$VERSION" ]; then
  echo "[0/X] 更新 pluginVersion → $VERSION ..."
  if [ ! -f "$GRADLE_PROPERTIES" ]; then
    echo "错误: 找不到 $GRADLE_PROPERTIES" >&2
    exit 1
  fi
  # 仅替换 pluginVersion=... 这一行, 其他属性原样保留
  # 用 perl 是为了避免 macOS / GNU sed 的 -i 行为差异
  perl -i -pe "s/^pluginVersion=.*/pluginVersion=$VERSION/" "$GRADLE_PROPERTIES"
  CURRENT=$(grep '^pluginVersion=' "$GRADLE_PROPERTIES" | cut -d= -f2)
  if [ "$CURRENT" != "$VERSION" ]; then
    echo "错误: 版本号未成功写入 (当前为 '$CURRENT')" >&2
    exit 1
  fi
  echo "✓ pluginVersion 已更新为 $VERSION"
  echo ""
fi

############################################
# 1) 发布到 JetBrains Marketplace
############################################
if $do_publish; then
  echo "[1/X] 执行 ./gradlew clean publishDefault --no-daemon ..."
  (cd "$SCRIPT_DIR" && ./gradlew clean publishDefault --no-daemon)
  echo "✓ Marketplace 默认通道发布完成"
  echo "   https://plugins.jetbrains.com/plugin/$PLUGIN_MARKETPLACE_ID"
  echo ""
fi

if $do_publish_beta; then
  echo "[1b/X] 执行 ./gradlew clean publishBeta --no-daemon ..."
  (cd "$SCRIPT_DIR" && ./gradlew clean publishBeta --no-daemon)
  echo "✓ Marketplace beta 通道发布完成"
  echo "   https://plugins.jetbrains.com/plugin/$PLUGIN_MARKETPLACE_ID"
  echo ""
fi

############################################
# 2) 上传插件 ZIP 到服务器
############################################
if $do_zip; then
  echo "[2/X] 上传插件 ZIP 到服务器 ..."

  # 没有构建产物就先 buildPlugin
  if [ ! -d "$ZIP_DIR" ]; then
    echo "未发现 $ZIP_DIR, 先执行 buildPlugin ..."
    (cd "$SCRIPT_DIR" && ./gradlew buildPlugin --no-daemon)
  fi

  # 取最新一份 zip
  ZIP_FILE=$(ls -t "$ZIP_DIR"/${PLUGIN_NAME}-*.zip 2>/dev/null | head -n1 || true)
  if [ -z "$ZIP_FILE" ]; then
    echo "未找到 ${PLUGIN_NAME}-*.zip, 重新执行 buildPlugin ..."
    (cd "$SCRIPT_DIR" && ./gradlew buildPlugin --no-daemon)
    ZIP_FILE=$(ls -t "$ZIP_DIR"/${PLUGIN_NAME}-*.zip 2>/dev/null | head -n1 || true)
    if [ -z "$ZIP_FILE" ]; then
      echo "错误: 构建后仍未找到 ${PLUGIN_NAME}-*.zip" >&2
      exit 1
    fi
  fi

  DEST_ZIP_NAME="${PLUGIN_NAME}.zip"
  echo "✓ ZIP 文件: $ZIP_FILE"
  echo "正在上传到 $REMOTE_HOST:$REMOTE_ROOT_DIR/$DEST_ZIP_NAME ..."
  ssh "$REMOTE_HOST" "mkdir -p $REMOTE_ROOT_DIR"
  rsync -avz --progress "$ZIP_FILE" "$REMOTE_HOST:$REMOTE_ROOT_DIR/$DEST_ZIP_NAME"
  ssh "$REMOTE_HOST" "chmod 644 $REMOTE_ROOT_DIR/$DEST_ZIP_NAME"

  # 可选: 推到独立的下载站目录 (例如 /var/www/data/...)
  if [ -n "$REMOTE_DATA_DIR" ]; then
    echo "正在推送到独立下载站 $REMOTE_HOST:$REMOTE_DATA_DIR/ ..."
    ssh "$REMOTE_HOST" "mkdir -p $REMOTE_DATA_DIR"
    # 这里保留原始带版本号的文件名, 方便下载站做归档
    rsync -avz --progress "$ZIP_FILE" "$REMOTE_HOST:$REMOTE_DATA_DIR/"
  fi

  echo "✓ ZIP 上传完成"
  echo ""
fi

############################################
# 3) 部署 landing 站点
############################################
if $do_site; then
  echo "[3/X] 部署 landing 站点 ..."

  if [ ! -d "$LANDING_DIR" ]; then
    echo "错误: 找不到 landing 目录: $LANDING_DIR" >&2
    exit 1
  fi
  if [ ! -f "$LANDING_DIR/index.html" ]; then
    echo "错误: $LANDING_DIR/index.html 不存在" >&2
    exit 1
  fi

  ssh "$REMOTE_HOST" "mkdir -p $REMOTE_ROOT_DIR"

  # 全量同步, --delete 保证服务器与本地完全一致.
  # 排除 nginx 配置 (NGINX_CONF_NAME) — 它走 -n 单独部署到 /etc/nginx/conf.d/,
  # 不能跟着 root 一起放 (放进站点 root 反而会被当成静态资源外泄).
  # --chmod=F644,D755 — 强制把所有文件落地成 644 / 目录 755,
  # 防止本地偶尔出现的 600 文件 (比如 svg / png 被某些工具误设权限)
  # 同步到远程后被 nginx worker 读不到, 返回 403.
  rsync -avz --delete --progress \
    --chmod=F644,D755 \
    --exclude "$NGINX_CONF_NAME" \
    --exclude 'README.md' \
    --exclude '.DS_Store' \
    --exclude '*.log' \
    "$LANDING_DIR/" \
    "$REMOTE_HOST:$REMOTE_ROOT_DIR/"

  echo "✓ landing 站点部署完成"
  echo ""
fi

############################################
# 4) 部署 nginx 配置 + 远程 reload
############################################
if $do_nginx; then
  echo "[4/X] 部署 nginx 配置 ..."

  if [ ! -f "$NGINX_CONF_FILE" ]; then
    echo "错误: 找不到 $NGINX_CONF_FILE" >&2
    exit 1
  fi

  echo "上传 $NGINX_CONF_FILE → $REMOTE_HOST:$REMOTE_NGINX_CONF_DIR/$NGINX_REMOTE_CONF_NAME ..."
  rsync -avz --copy-links --progress \
    "$NGINX_CONF_FILE" \
    "$REMOTE_HOST:$REMOTE_NGINX_CONF_DIR/$NGINX_REMOTE_CONF_NAME"

  echo "远程 nginx -t && systemctl reload nginx ..."
  # 用 reload 而非 restart, 不中断已建立连接
  ssh "$REMOTE_HOST" "nginx -t && systemctl reload nginx"

  echo "✓ nginx 配置部署完成并 reload"
  echo ""
fi

############################################
# 收尾输出
############################################
echo "================================"
echo "✓ 部署流程结束"
$do_publish      && echo "  - Marketplace 默认通道: https://plugins.jetbrains.com/plugin/$PLUGIN_MARKETPLACE_ID"
$do_publish_beta && echo "  - Marketplace beta 通道: https://plugins.jetbrains.com/plugin/$PLUGIN_MARKETPLACE_ID (审核后可见)"
$do_zip          && echo "  - ZIP: $REMOTE_HOST:$REMOTE_ROOT_DIR/${PLUGIN_NAME}.zip"
$do_site         && echo "  - 站点: $SITE_URL  (中文: $SITE_URL/zh/)"
$do_nginx        && echo "  - nginx: $REMOTE_HOST:$REMOTE_NGINX_CONF_DIR/$NGINX_REMOTE_CONF_NAME (已 reload)"
echo "================================"
