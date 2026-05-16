# Middleware Resource Manager

基于 `Spring Boot 3 + JDK 17 + MySQL` 的中间件文件管理平台。

## 功能

- 管理员登录后台维护版本信息
- 上传中间件安装包并记录文件元数据
- 文件按中间件名称自动归档到本地目录
- 维护版本号、平台、发布日期、版本说明、发布状态
- 自动生成公开详情页和下载直链
- 统计下载次数

## 技术栈

- Spring Boot 3.3.5
- Spring MVC
- Spring Security
- Spring Data JPA
- Thymeleaf
- MySQL 8.4.x

## 本地目录

- MySQL 程序目录：`./tools/mysql-8.4.9-winx64`
- MySQL 数据目录：`./mysql/data`
- MySQL 配置文件：`./mysql/my.ini`
- 上传文件目录：`./storage/<middleware-name>/`

## 默认配置

- 管理后台：`http://localhost:8080/admin/releases`
- 登录页：`http://localhost:8080/login`
- 公开下载页：`http://localhost:8080/downloads`
- 默认管理员账号：`admin`
- 默认管理员密码：`admin123`
- MySQL 地址：`127.0.0.1:3306`
- MySQL 用户：`root`
- MySQL 密码：`SuNfgbZjdm-UnLZ4P4QH`

## 启动 MySQL

在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-mysql.ps1
```

停止 MySQL：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\stop-local-mysql.ps1
```

## 启动项目

直接运行打包后的 JAR：

```powershell
java -jar target\middleware-resource-manager-0.0.1-SNAPSHOT.jar
```

或者使用 Maven：

```powershell
mvn "-Dmaven.repo.local=.m2" spring-boot:run
```

## 数据库连接配置

应用默认读取以下环境变量；未设置时使用本地默认值：

- `APP_DB_HOST`，默认 `127.0.0.1`
- `APP_DB_PORT`，默认 `3306`
- `APP_DB_NAME`，默认 `middleware_resource_manager`
- `APP_DB_USERNAME`，默认 `root`
- `APP_DB_PASSWORD`，默认 `SuNfgbZjdm-UnLZ4P4QH`

## 首次运行说明

1. 先启动本地 MySQL。
2. 再启动 Spring Boot 应用。
3. 应用会自动创建数据库 `middleware_resource_manager` 和表结构。
4. 首次登录后台后，建议修改 `src/main/resources/application.yml` 中的默认管理员账号密码。
