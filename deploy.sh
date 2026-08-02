#!/bin/bash
# 家收纳 - 一键部署脚本
# 在服务器上运行: bash deploy.sh

set -e
cd "$(dirname "$0")"

echo "🔄 正在拉取最新代码..."
git pull origin main

echo "✅ 部署完成！"
echo ""
echo "📋 更新内容："
echo "  - 备份管理系统（数据库备份/图片打包/全量导出/数据恢复）"
echo "  - 修复编辑物品保存变新建的BUG"
echo "  - 新增临期提醒管理页面"
echo ""
echo "🌐 访问管理后台: http://你的域名/web-admin/"
