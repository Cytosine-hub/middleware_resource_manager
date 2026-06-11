# Release v1.2.1-20260611

**日期**: 2026-06-11
**分支**: feature/ops-agent
**发布范围**: frontend + backend
**类型**: 增量发布

## 自 v1.2.0-20260611 以来的变更

- 82d1dd7 fix: stabilize wiki import signature errors

## 包内容

| 文件 | 说明 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 可执行 JAR |
| backend/application.yml.example | 配置示例 |
| frontend/ | 前端静态资源 |

---

# Release v1.2.0-20260611

**日期**: 2026-06-11
**分支**: feature/ops-agent
**发布范围**: frontend + backend
**类型**: 增量发布

## 自 v1.1.2-20260610 以来的变更

- 135ce55 fix: 编辑资源信息时不再显示上传中状态
- ba1928f fix: 修复系统设置开关不生效 + 新增 Wiki 开关
- ed563b8 feat: 用户管理增加重置密码功能
- 634e511 fix: 修复导入弹窗按钮点击无响应
- 6d29c13 fix: 修复批量导入用户按钮无响应和样式问题
- 8363312 feat: 批量导入用户功能
- 654f341 fix: 新增用户对话框加载角色列表

## 包内容

| 文件 | 说明 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 可执行 JAR |
| backend/application.yml.example | 配置示例 |
| frontend/ | 前端静态资源 |

---

# Release v1.1.2-20260610

**日期**: 2026-06-10
**分支**: feature/ops-agent
**发布范围**: frontend + backend + db
**类型**: 增量发布

## 自 v1.1.1-20260610 以来的变更

- 76883e9 add frontend browser version gate
- 067c8db fix word preview document navigation
- fb04d7c fix frontend pdf preview scrolling

## 包内容

| 文件 | 说明 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 可执行 JAR |
| backend/application.yml.example | 配置示例 |
| frontend/ | 前端静态资源 |
| db/full_schema.sql | 完整表结构（DDL） |
| db/seed_data.sql | 种子数据（DML） |

---

# Release v1.1.1-20260610

**日期**: 2026-06-10
**分支**: feature/ops-agent
**发布范围**: full（前端 + 后端 + 数据库 + 文档）
**类型**: 全量发布

## 主要变更

### 新功能
- feat: 标准文档增加 PDF 格式支持
- feat: Word 文档支持"开始修改"流程（替换文档 + 编辑元数据）
- feat: Word 预览支持参数占位符替换
- feat: Word 文档上传完整流程 + docx-preview 源文档渲染
- feat: 标准详情页关联手册改为标准文档，卡片流式布局
- feat: 论坛列表无限滚动懒加载

### 修复
- fix: split document preview components
- fix: align document upload preview flow
- fix: 精准修复标准页粘性侧边栏，不影响管理后台固定布局
- fix: 将 .workspace height:100% 改为 min-height:100% 修复粘性侧边栏
- fix: 修复标准页 Word 文档长时左侧目录不粘性的问题
- fix: Word 文档不显示文档大纲
- fix: 标准发布页文档点击无响应 + Word 文档预览支持
- fix: 补充 .status.warn 样式定义，修复待审核状态显示异常
- fix: 修复 Word 文档审核流程中的多个问题
- fix: StandardDocumentResponse 添加 storedFileName，Word 预览样式
- fix word preview document navigation
- fix frontend pdf preview scrolling

### 优化
- chore: 优化 code-review hook 和 skill，减少误报
- chore: 新增 db 目录存储初始化 SQL 和种子数据

## 包内容

```
middleware-resource-manager-v1.1.1-20260610/
├── backend/
│   ├── middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar  # 可执行 JAR
│   └── application.yml.example                             # 配置示例
├── frontend/
│   ├── index.html
│   ├── favicon.svg
│   └── assets/                                             # 前端静态资源
├── db/
│   ├── full_schema.sql                                     # 完整表结构（DDL）
│   └── seed_data.sql                                       # 种子数据（DML）
└── release.md                                              # 本文件
```

## 部署说明

### 新环境部署

1. 创建数据库：
```sql
CREATE DATABASE middleware_resource_manager DEFAULT CHARACTER SET utf8mb4;
```

2. 导入表结构：
```sql
mysql -u root middleware_resource_manager < db/full_schema.sql
```

3. 导入种子数据：
```sql
mysql -u root middleware_resource_manager < db/seed_data.sql
```

4. 复制并修改配置：
```bash
cp backend/application.yml.example backend/application.yml
# 编辑 application.yml，配置数据库连接、AI API Key 等
```

5. 启动后端：
```bash
java -jar backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
```

6. 部署前端：
将 `frontend/` 目录部署到 Nginx 或其他 Web 服务器。

### 从旧版本升级

1. 备份数据库：
```sql
mysqldump -u root middleware_resource_manager > backup_20260610.sql
```

2. 对比表结构差异，手动添加缺失列

3. 替换后端 JAR 和前端文件

4. 重启服务

## 文件清单

| 文件 | 说明 |
|------|------|
| backend/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar | 可执行 JAR（约 107M） |
| backend/application.yml.example | 配置示例文件 |
| frontend/index.html | 前端入口 |
| frontend/assets/ | 前端静态资源（JS/CSS） |
| db/full_schema.sql | 完整表结构（DDL） |
| db/seed_data.sql | 种子数据（DML） |

## 数据库表清单（32 张）

| 类别 | 表名 | 说明 |
|------|------|------|
| 核心 | admin_accounts | 管理员账号 |
| 核心 | software_categories | 软件分类 |
| 核心 | software_types | 软件类型 |
| 核心 | release_assets | 发布资源 |
| 核心 | standard_documents | 标准文档 |
| 核心 | standard_parameters | 标准参数 |
| 核心 | parameter_standards | 参数标准 |
| 核心 | review_records | 审核记录 |
| 核心 | roles | 角色 |
| 核心 | system_settings | 系统设置 |
| 核心 | user_tokens | 用户令牌 |
| 核心 | document_revisions | 文档修订历史 |
| 命令 | middleware_commands | 常用命令 |
| 论坛 | forum_posts | 论坛帖子 |
| 论坛 | forum_comments | 论坛评论 |
| 论坛 | forum_tags | 论坛标签 |
| 论坛 | forum_post_tags | 帖子标签关联 |
| 论坛 | forum_post_likes | 帖子点赞 |
| AI | chat_sessions | AI 对话会话 |
| AI | chat_messages | AI 对话消息 |
| AI | agent_tool_invocations | Agent 工具调用审计 |
| 知识库 | knowledge_chunks | 知识库切片 |
| Wiki | wiki_pages | Wiki 页面 |
| Wiki | wiki_links | Wiki 页面关系 |
| Wiki | wiki_sources | Wiki 原始文档 |
| Wiki | wiki_ingest_tasks | Wiki 编译任务 |
| Wiki | wiki_ingest_log | Wiki 编译日志 |
| Wiki | wiki_lint_results | Wiki 质量检查 |
| Wiki | wiki_audit_log | Wiki 操作审计 |
| Wiki | wiki_page_permissions | Wiki 页面权限 |
| Wiki | wiki_access_requests | Wiki 访问申请 |

## 环境要求

- Java 17+
- MySQL 8.0+
- Node.js 18+（仅开发环境）

## 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| sysadmin | sysadmin123 | 系统管理员 |
| mwadmin | mwadmin123 | 中间件管理岗 |
| dbadmin | dbadmin123 | 数据库管理岗 |
| hostadmin | hostadmin123 | 主机管理岗 |
| netadmin | netadmin123 | 网络管理岗 |
| secadmin | secadmin123 | 安全管理岗 |
| devmgr | devmgr123 | 开发经理 |
| opsmgr | opsmgr123 | 运维经理 |

## 配置说明

主要配置项（在 `application.yml` 中）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/middleware_resource_manager
    username: root
    password: your_password

langchain4j:
  open-ai:
    chat-model:
      api-key: your_ai_api_key
      base-url: https://your_ai_endpoint/v1
```

## 相关文档

- 开发规范：`docs/development-standards.md`
- Zabbix 集成：`docs/zabbix-integration-guide.md`
- 知识库 DDL：`src/main/resources/db/knowledge_ddl.sql`
