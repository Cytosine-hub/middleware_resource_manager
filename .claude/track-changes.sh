#!/bin/bash
# 变更追踪脚本 - 自动记录 DB 和配置文件的修改
# 由 PostToolUse hook 调用

FILE_PATH="$1"
CHANGES_FILE="/Users/zhushihao/Projects/infra_portal/.claude/changes.md"
DATE=$(date +%Y-%m-%d)

# 判断是否为需要追踪的文件
track_change() {
    local file="$1"
    local type=""
    local desc=""

    # DB 相关文件
    if [[ "$file" == *"/db/"*".sql" ]]; then
        type="DB"
        desc="SQL 脚本变更"
    elif [[ "$file" == *"init.sql"* ]] || [[ "$file" == *"seed.sql"* ]]; then
        type="DB"
        desc="数据库初始化/种子数据变更"
    elif [[ "$file" == *"migration/"* ]]; then
        type="DB"
        desc="数据库迁移脚本"
    elif [[ "$file" == *"ddl.sql"* ]] || [[ "$file" == *"dml.sql"* ]]; then
        type="DB"
        desc="DDL/DML 脚本变更"

    # 配置相关文件
    elif [[ "$file" == *"application.yml"* ]] || [[ "$file" == *"application.yaml"* ]]; then
        type="CONFIG"
        desc="应用配置变更"
    elif [[ "$file" == *"application-"*".yml"* ]]; then
        type="CONFIG"
        desc="环境配置变更"
    elif [[ "$file" == *"pom.xml"* ]]; then
        type="CONFIG"
        desc="Maven 依赖变更"
    elif [[ "$file" == *"vite.config"* ]]; then
        type="CONFIG"
        desc="Vite 构建配置变更"
    elif [[ "$file" == *".env"* ]] || [[ "$file" == *".env."* ]]; then
        type="CONFIG"
        desc="环境变量变更"
    elif [[ "$file" == *"docker-compose"* ]]; then
        type="CONFIG"
        desc="Docker 配置变更"
    elif [[ "$file" == *"nginx"*".conf"* ]]; then
        type="CONFIG"
        desc="Nginx 配置变更"
    fi

    # 如果匹配到需要追踪的类型，记录到文件
    if [[ -n "$type" ]]; then
        # 获取相对路径
        local rel_path="${file#/Users/zhushihao/Projects/infra_portal/}"

        # 检查是否已存在相同记录（避免重复）
        if ! grep -q "$rel_path" "$CHANGES_FILE" 2>/dev/null; then
            # 在 CHANGES_END 标记前插入新记录
            sed -i '' "/<!-- CHANGES_END -->/i\\
- [$type] $DATE $rel_path — $desc
" "$CHANGES_FILE"
        fi
    fi
}

# 执行追踪
if [[ -n "$FILE_PATH" ]]; then
    track_change "$FILE_PATH"
fi
