# 项目启动手册

本文档适用于当前前后端分离版本：

- 后端：Spring Boot 3.5.3，默认端口 `8080`
- 前端：Vue 3 + Vite，默认端口 `5173`
- 数据库：MySQL 8.0，默认端口 `3306`

## 1. 环境要求

- JDK 17（必须，Spring Boot 3.x 不兼容 JDK 8）
- Maven 3.8.x
- Node.js 18+，当前环境已验证 Node.js 22 可用
- MySQL 8.x

### JAVA_HOME 配置

确保 `JAVA_HOME` 指向 JDK 17：

```bash
# macOS / Linux（添加到 ~/.zshrc 或 ~/.bashrc）
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Windows 下在系统环境变量中设置 `JAVA_HOME` 为 JDK 17 安装路径。

Windows PowerShell 下建议使用 `npm.cmd`，避免 `npm.ps1` 被执行策略拦截。

## 2. 启动数据库

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-mysql.ps1
```

停止数据库：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-mysql.ps1
```

默认数据库配置在 `src/main/resources/application.yml`：

- 数据库：`middleware_resource_manager`
- 地址：`127.0.0.1:3306`
- 用户：`root`
- 密码：读取 `APP_DB_PASSWORD`，未设置时使用配置文件默认值

## 3. 启动后端

在项目根目录执行：

```powershell
mvn -gs maven-local-settings.xml -s maven-local-settings.xml "-Dmaven.repo.local=.m2" spring-boot:run
```

后端启动成功后访问：

```text
http://localhost:8080/api/public/releases
```

如果返回 JSON，说明后端接口可用。

说明：当前机器的 Maven 全局 `settings.xml` 有解析问题，所以启动命令显式使用项目内的 `maven-local-settings.xml`。

## 4. 启动前端

进入前端目录：

```powershell
cd .\frontend
```

首次启动前安装依赖：

```powershell
npm.cmd install
```

启动 Vue 开发服务器：

```powershell
npm.cmd run dev
```

前端访问地址：

```text
http://localhost:5173
```

Vite 已配置代理：

- `/api` 转发到 `http://localhost:8080`
- `/files` 转发到 `http://localhost:8080`

## 5. 登录后台

打开前端：

```text
http://localhost:5173/#/admin
```

使用数据库中的管理员账号登录。

默认账号配置为：

- 用户名：`admin`
- 密码：`admin123`

注意：默认账号密码只会在管理员表为空时初始化。如果数据库里已经有管理员账号，实际密码以数据库现有数据为准。

## 6. 常用页面

| 页面 | 地址 | 说明 |
|------|------|------|
| 门户首页 | `http://localhost:5173/#/home` | 公开入口，聚合下载、标准、漏洞、论坛 |
| 下载中心 | `http://localhost:5173/#/downloads` | 中间件资源下载 |
| 标准发布 | `http://localhost:5173/#/standards` | 已发布的参数标准和标准文档 |
| 论坛 | `http://localhost:5173/#/forum` | 技术交流 |
| 知识库管理 | `http://localhost:5173/#/knowledge` | 文档上传、切分、向量化 |
| 智能排查 | `http://localhost:5173/#/diagnostics` | 基于知识库的 AI 排查对话 |
| 管理后台 | `http://localhost:5173/#/admin` | 需登录，含以下子模块 |

管理后台子模块：

| 子模块 | 说明 |
|--------|------|
| 文件管理 | 中间件资源上传、编辑、发布 |
| 类型管理 | 软件分类和软件类型维护 |
| 参数标准 | 参数标准的创建、编辑、发布、版本管理 |
| 标准文档 | 手册/文章的编写和管理 |
| 审核管理 | 参数标准和标准文档的审核流程 |
| 用户管理 | 管理员账号和角色（仅系统管理员可见） |

后端 API：

- 公开资源列表：`http://localhost:8080/api/public/releases`
- 文件下载接口：`http://localhost:8080/files/{downloadToken}`
- 公开参数标准：`http://localhost:8080/api/public/parameter-standards`

## 7. 构建前端

进入 `frontend` 目录执行：

```powershell
npm.cmd run build
```

构建产物输出到：

```text
frontend/dist
```

## 8. 后端测试

在项目根目录执行：

```powershell
mvn -gs maven-local-settings.xml -s maven-local-settings.xml "-Dmaven.repo.local=.m2" test
```

## 9. 端口占用检查

检查后端端口：

```powershell
netstat -ano | Select-String ':8080'
```

检查前端端口：

```powershell
netstat -ano | Select-String ':5173'
```

## 10. 推荐启动顺序

1. 启动 MySQL
2. 启动 Spring Boot 后端
3. 启动 Vue 前端
4. 打开 `http://localhost:5173`

如果前端页面能打开但接口报错，优先检查后端 `8080` 是否启动成功；如果后端启动失败，优先检查 MySQL 是否已启动。

## 11. 知识库模块配置

### 1. 执行 DDL

首次运行需手动创建知识库相关表：

```bash
mysql -u root -p middleware_resource_manager < src/main/resources/db/knowledge_ddl.sql
```

### 2. 配置 AI 模型

设置环境变量或修改 `application.yml`：

```bash
export AI_BASE_URL=https://your-model-api/v1
export AI_API_KEY=your-api-key
export AI_MODEL=your-model-name
```

### 3. 前端页面

- 知识库管理：`http://localhost:5173/#/knowledge`
- 智能排查：`http://localhost:5173/#/diagnostics`
