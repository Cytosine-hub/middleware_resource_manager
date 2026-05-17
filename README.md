# Middleware Resource Manager

基于 `Spring Boot 2.7 + Java 8 + MySQL 8.0` 的中间件文件管理平台。

## 功能

- 管理员登录后台维护版本信息
- 上传中间件安装包并记录文件元数据
- 文件按中间件名称自动归档到本地目录
- 维护版本号、平台、发布日期、版本说明、发布状态
- 自动生成公开详情页和下载直链
- 统计下载次数

## 技术栈

- Spring Boot 2.7.18
- Spring MVC
- Spring Security
- Spring Data JPA
- Thymeleaf
- Vue 3 + Vite（前端）
- MySQL 8.0.x

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

- 管理后台：`http://localhost:8080/admin/releases`
- 登录页：`http://localhost:8080/login`
- 公开下载页：`http://localhost:8080/downloads`
- 前端开发：`http://localhost:5173`

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
