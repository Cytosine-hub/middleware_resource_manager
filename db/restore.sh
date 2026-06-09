#!/bin/bash
# 数据库一键恢复脚本
# 用法: bash db/restore.sh [数据库名] [用户名]

DB_NAME="${1:-middleware_resource_manager}"
DB_USER="${2:-root}"
DB_HOST="${APP_DB_HOST:-127.0.0.1}"
DB_PORT="${APP_DB_PORT:-3306}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=========================================="
echo "  数据库恢复"
echo "=========================================="
echo "  数据库: $DB_NAME"
echo "  用户:   $DB_USER"
echo "  地址:   $DB_HOST:$DB_PORT"
echo "=========================================="
echo ""

read -p "确认恢复？这将清空现有数据 (y/N): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
    echo "已取消"
    exit 0
fi

echo ""
echo "[1/2] 执行建表语句..."
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p "$DB_NAME" < "$SCRIPT_DIR/init.sql"
if [ $? -ne 0 ]; then
    echo "❌ 建表失败"
    exit 1
fi
echo "✅ 建表完成"

echo ""
echo "[2/2] 导入种子数据..."
mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p "$DB_NAME" < "$SCRIPT_DIR/seed.sql"
if [ $? -ne 0 ]; then
    echo "❌ 种子数据导入失败"
    exit 1
fi
echo "✅ 种子数据导入完成"

echo ""
echo "=========================================="
echo "  恢复完成！"
echo "  默认账号: sysadmin / admin123"
echo "=========================================="
