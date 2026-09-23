#!/data/data/com.termux/files/usr/bin/bash
# 推送到 GitHub 触发 Actions 构建
# 用法: GH_USER=你的用户名 GH_TOKEN=新token GH_REPO=repo名 ./push.sh
#
# 注意: token 别写进脚本/别贴聊天，用环境变量传。

set -e

GH_USER="${GH_USER:?请设置 GH_USER}"
GH_TOKEN="${GH_TOKEN:?请设置 GH_TOKEN (新生成的)}"
GH_REPO="${GH_REPO:-NewHook}"

cd "$(dirname "$0")"

# 初始化本地仓库（首次）
if [ ! -d .git ]; then
    git init
    git branch -M main
    git config user.email "hook@local"
    git config user.name "hook"
fi

# 建 .gitignore
cat > .gitignore <<'EOF'
build/
.gradle/
local.properties
*.iml
.idea/
app/build/
EOF

git add -A
git commit -m "NewHook: init (Xposed module, luna.music)" || echo "无变更可提交"

# 用 token 推送
git remote remove origin 2>/dev/null || true
git remote add origin "https://${GH_USER}:${GH_TOKEN}@github.com/${GH_USER}/${GH_REPO}.git"

git push -u origin main --force

echo ""
echo "✅ 已推送。去 GitHub → Actions 看构建结果，"
echo "   编完在 Artifacts 里下载 NewHook-debug.apk"