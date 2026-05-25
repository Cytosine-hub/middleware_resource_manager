# Middleware Resource Manager

基于 `Spring Boot 3.5 + Java 17 + MySQL 8.0` 的中间件文件管理平台。

## 功能

- 管理员登录后台维护版本信息
- 上传中间件安装包并记录文件元数据
- 文件按中间件名称自动归档到本地目录
- 维护版本号、平台、发布日期、版本说明、发布状态
- 自动生成公开详情页和下载直链
- 统计下载次数
- 参数标准管理：创建、编辑、发布、版本管理（草稿→审核→发布→修改）
- 标准文档管理：手册/文章编写，关联参数标准，支持 {{参数名}} 占位符自动替换
- 审核流程：提交审核、审批通过/驳回，审核时可对比参数值变更差异
- 标准发布页面：按分类展示已发布的参数标准和关联手册
- 论坛模块：发帖、评论、点赞、标签分类
- 知识库：上传技术文档（PDF/Word/Markdown），自动切分和向量化，支持语义检索
- 智能排查：基于知识库的 RAG 对话，AI 辅助故障诊断

## 技术栈

- Spring Boot 3.5.3
- Spring MVC
- Spring Security
- Spring Data JPA
- Thymeleaf
- LangChain4j（AI/LLM 集成框架）
- Milvus（向量数据库）
- Vue 3 + Vite（前端）
- MySQL 8.0.x
- Java 17

## 本地目录

- 上传文件目录：`./storage/<middleware-name>/`
- 应用日志：`./logs/middleware-resource-manager.log`

## 管理员账号

系统预设了以下管理员账号（密码均为 `admin123`）：

| 用户名 | 角色 | 显示名称 |
|--------|------|----------|
| `sysadmin` | 系统管理员 | 系统管理员 |
| `mwadmin` | 中间件管理岗 | 中间件管理员 |
| `dbadmin` | 数据库管理岗 | 数据库管理员 |
| `hostadmin` | 主机管理岗 | 主机管理员 |
| `netadmin` | 网络管理岗 | 网络管理员 |
| `secadmin` | 网络安全岗 | 安全管理员 |
| `devmgr` | 开发经理 | 开发经理 |
| `opsmgr` | 运维经理 | 运维经理 |

## 访问地址

- 前端（开发）：`http://localhost:5173`
- 门户首页：`http://localhost:5173/#/home`
- 下载中心：`http://localhost:5173/#/downloads`
- 标准发布：`http://localhost:5173/#/standards`
- 论坛：`http://localhost:5173/#/forum`
- 知识库：`http://localhost:5173/#/knowledge`
- 智能排查：`http://localhost:5173/#/diagnostics`
- 管理后台：`http://localhost:5173/#/admin`

## 启动应用

**后端（Spring Boot）：**

```bash
# 编译
mvn clean package -DskipTests

# 启动
mvn spring-boot:run
```

**前端（Vue 3 + Vite）：**

```bash
cd frontend
npm install
npm run dev
```

## 数据库连接配置

应用默认读取以下环境变量；未设置时使用本地默认值：

| 环境变量 | 默认值 |
|----------|--------|
| `APP_DB_HOST` | `127.0.0.1` |
| `APP_DB_PORT` | `3306` |
| `APP_DB_NAME` | `middleware_resource_manager` |
| `APP_DB_USERNAME` | `root` |
| `APP_DB_PASSWORD` | `OlgDqdJfehRwBUITqFpi` |

## 首次运行说明

1. 确保本地 MySQL 8.0 已启动。
2. 创建数据库：`CREATE DATABASE IF NOT EXISTS middleware_resource_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
3. 导入初始数据（如有 SQL 导出文件）。
4. 启动 Spring Boot 应用，应用会自动创建/更新表结构。
5. 登录后建议修改默认密码。

## 知识库 & 智能排查

### 功能

- **知识库管理**：上传技术文档（PDF/Word/Markdown），自动切分和向量化，支持语义检索
- **智能排查**：基于知识库的 RAG 对话，AI 辅助故障诊断和排查建议
- **已有文档导入**：可将系统中的标准文档一键导入知识库

### 数据库表

需要手动执行 DDL 脚本创建表：

```bash
mysql -u root -p middleware_resource_manager < src/main/resources/db/knowledge_ddl.sql
```

| 表名 | 用途 |
|------|------|
| `knowledge_chunks` | 知识库文本切片及元数据 |
| `chat_sessions` | AI 对话会话 |
| `chat_messages` | AI 对话消息记录 |

### API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/knowledge/upload` | 上传文件入库 |
| POST | `/api/knowledge/import/{docId}` | 导入已有标准文档 |
| GET | `/api/knowledge/search?q=xxx&topK=5` | 知识库语义检索 |
| POST | `/api/agent/chat` | 发送排查对话消息 |
| GET | `/api/agent/sessions` | 获取所有会话列表 |
| GET | `/api/agent/sessions/{id}` | 获取会话消息历史 |

### 配置项

在 `application.yml` 中配置：

| 配置项 | 环境变量 | 默认值 | 说明 |
|--------|----------|--------|------|
| `app.ai.base-url` | `AI_BASE_URL` | `https://token-plan-cn.xiaomimimo.com/v1` | 大模型 API 地址 |
| `app.ai.api-key` | `AI_API_KEY` | `your-api-key` | API Key |
| `app.ai.model` | `AI_MODEL` | `mimo-v2.5-pro` | 模型名称 |
| `app.ai.max-tokens` | `AI_MAX_TOKENS` | `4096` | 最大生成 token 数 |
| `app.ai.temperature` | `AI_TEMPERATURE` | `0.1` | 生成温度 |
