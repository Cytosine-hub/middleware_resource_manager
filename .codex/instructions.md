# Codex Instructions

## ⚠️ 强制规则：使用 CodeGraph 查看代码

**查看代码时必须优先使用 CodeGraph MCP 工具，禁止直接读取整个文件。**

### 使用场景

| 场景 | 使用工具 | 说明 |
|------|---------|------|
| 查找函数/类定义 | `codegraph_search` | 搜索符号位置 |
| 查看函数实现 | `codegraph_node` | 获取节点详情 |
| 查找谁调用了某函数 | `codegraph_callers` | 分析调用链 |
| 查看函数调用了什么 | `codegraph_callees` | 分析依赖 |
| 评估修改影响范围 | `codegraph_impact` | 影响分析 |
| 浏览项目结构 | `codegraph_files` | 文件树 |

### 禁止行为

- ❌ 直接读取整个 Java/Vue 文件来了解结构
- ❌ 用 grep 搜索函数定义（应用 `codegraph_search`）
- ❌ 用 find 查找文件（应用 `codegraph_files`）

### 正确流程

```
用户询问"UserService 在哪里"
    ↓
codegraph_search("UserService")
    ↓
获取文件路径和行号
    ↓
如需查看详情：codegraph_node("UserService")
```

## 项目信息

- 项目根目录: /Users/zhushihao/Projects/middleware_resource_manager
- 后端: Spring Boot 3.5.3, Java 17
- 前端: Vue 3 + Vite
- 数据库: MySQL 8.0
