# 后端微服务化阶段 4：core-service 剥离说明

## 1. 阶段结果与拓扑

本阶段把 identity、catalog、standards 作为一个闭环边界上下文，从 app 整体剥离到独立 Spring Boot 应用 `core-service`。三者继续在同一进程内通过 `AccountDirectory`、`SoftwareTypeLookup`、`StandardPackageOperations` 直接调用，不增加 HTTP/RPC，不改 Controller 路径、鉴权规则、SQL、数据库 schema 或 DML。

```text
Vue / Vite (:5173)
        |
        v
api-gateway (:8080)
        |-- /api/forum/** --------------------------> community-service (:8082)
        |-- AI/Agent 原路径 ------------------------> ai-service (:8083)
        |-- identity/catalog/standards 原路径 ------> core-service (:8084)
        |-- /api/public/**、/files/** --------------> core-service (:8084)
        `-- 其余 /api/** --------------------------> app (:8081，岗位模块)
```

默认 profile 使用静态地址，五个进程均关闭 Nacos。`cloud` profile 下五个进程注册 Nacos，Gateway 分别使用 `lb://community-service`、`lb://ai-service`、`lb://core-service` 和 `lb://middleware-resource-manager-app`。

## 2. 构建与模块边界

五个可执行产物：

```text
backend/app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
backend/community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
backend/ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
backend/core-service/target/core-service-0.0.1-SNAPSHOT-exec.jar
```

`core-service` 显式聚合 `common-core`、`common-security`、`common-web`、`identity`、`catalog`、`standards`。app 已移除后三个业务模块依赖，只聚合 common-* 与五个 job-* 模块；当前实际业务端点为 job-middleware 的 `/api/middleware-commands/**`。

app 仍复用 common-security 校验共享 `user_tokens`，并通过 app 本地认证适配器读取 `admin_accounts`、`roles`。这是本阶段保持认证现状所需的横切读取，不把认证逻辑移动到 identity，也不做网关集中认证。

## 3. 原路径与 Gateway 路由

core 路由位于 app 泛路由之前，且不重写路径：

```text
/api/auth/**
/api/admin/users/**
/api/admin/account/**
/api/admin/settings/**
/api/admin/releases/**
/api/admin/software-types/**
/api/admin/software-type-categories/**
/api/admin/parameter-standards/**
/api/admin/standard-documents/**
/api/admin/standard-parameters/**
/api/admin/reviews/**
/api/admin/revisions/**
/api/admin/images/**
/api/public/**
/files/**
```

core-service 上下文契约固定加载 19 个既有 Controller、78 个 handler 映射。仓库源码共有 149 个方法级 Spring mapping 注解，其中包含既有内部清理端点 `/api/knowledge/docs/test`；按对外业务端点口径仍为 148，拆分前后 Controller 源码和端点集合均未变化。

## 4. 数据归属与 Mapper 白名单

一期继续共享 `middleware_resource_manager` MySQL 库，不执行任何 schema 或 DML 迁移。core-service 逻辑拥有：

```text
admin_accounts
roles
user_tokens
system_settings
api_audit_log
software_categories
software_types
release_assets
parameter_standards
standard_parameters
standard_documents
document_revisions
review_records
```

core-service 只加载认证、审计和上述三域所需的 13 份 Mapper XML。app 只加载认证读取、横切审计、岗位命令及其现有软件类型查询所需的 Mapper XML。共享库阶段保留其他服务的认证读取和审计写入；物理拆库及跨服务认证属于后续阶段。

## 5. 配置与密钥

core-service 默认端口为 `8084`，服务名为 `core-service`。默认 profile 显式关闭 Nacos；`cloud` profile 可选导入 `core-service.properties`。

数据库继续使用 `APP_DB_HOST`、`APP_DB_PORT`、`APP_DB_NAME`、`APP_DB_USERNAME`、`APP_DB_PASSWORD`。首次初始化空账号表前必须设置 `ADMIN_DEFAULT_PASSWORD`。数据库密码、管理员初始密码、Nacos 密码均使用 `${VAR:}` 空默认，仓库不内置真实密钥。

## 6. 默认 profile 多进程运行

```bash
cd backend
mvn -DskipTests clean package

java -jar app/target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar
java -jar community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
java -jar ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
java -jar core-service/target/core-service-0.0.1-SNAPSHOT-exec.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

Windows 可执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

app 与 core-service 也可独立构建、启动：

```bash
cd backend
mvn -pl app -am spring-boot:run
mvn -pl core-service -am spring-boot:run
```

## 7. 默认 profile 验证清单

```bash
# app 仅保留岗位端点
curl -i http://127.0.0.1:8081/api/middleware-commands/types

# app 不再携带 core Controller；带有效 Token 请求时应为 404
curl -i http://127.0.0.1:8081/api/admin/releases -H "Authorization: Bearer $TOKEN"

# core 公开端点和受保护端点
curl -i http://127.0.0.1:8084/api/public/config
curl -i http://127.0.0.1:8084/api/public/releases
curl -i http://127.0.0.1:8084/api/admin/releases

# Gateway 分流
curl -i http://127.0.0.1:8080/api/public/releases
curl -i http://127.0.0.1:8080/api/middleware-commands/types
curl -i http://127.0.0.1:8080/api/forum/posts
curl -i http://127.0.0.1:8080/api/wiki/pages
```

无 Token 的 `/api/auth/me` 和 `/api/admin/**` 应返回 401；公开路径保持无需登录。登录、上传、文件下载、审核和版本流使用原请求体、响应状态和 Bearer Token，不做格式转换。

## 8. cloud profile 验证清单

为五个进程设置一致的 `NACOS_SERVER_ADDR`、`NACOS_NAMESPACE` 和 `NACOS_DISCOVERY_GROUP`，再分别以 `-Dspring.profiles.active=cloud` 启动。检查 Nacos 中以下服务均有健康实例：

```text
api-gateway                         8080
middleware-resource-manager-app     8081
community-service                   8082
ai-service                          8083
core-service                        8084
```

停止 core-service 后，登录、下载、标准与管理后台路径应不可用，但论坛、AI 和 `/api/middleware-commands/**` 仍正常；恢复 core-service 后 Gateway 应随 Nacos 健康实例自动恢复。

## 9. 自动化验收

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

阶段 4 新增或强化的契约：

| TC | 验证点 |
|---|---|
| `TC-CORE-001` | 默认 profile、8084、Nacos 关闭、19 个 Controller 存在 |
| `TC-CORE-002` | core 仍注册 78 个既有 handler 映射 |
| `TC-CORE-003` | `/api/public/**` 与登录保持公开 |
| `TC-CORE-004` | auth/admin 继续要求 Bearer Token |
| `TC-CORE-005` | core 类路径不含岗位、论坛和 AI Controller |
| `TC-APP-005` | app 类路径不含 identity/catalog/standards Controller |
| `TC-APP-006` | app 仍加载 job-middleware Controller |
| `TC-GATEWAY-001` | 默认静态 core 路由指向 8084 |
| `TC-GATEWAY-002` | cloud core 路由使用 `lb://core-service` |

当前沙箱若禁止监听端口，只执行构建、上下文测试、JAR 和依赖检查；真实进程、Nacos 和 curl 联通按本清单在外部环境复验。

本地验收结果（2026-07-20）：

- `mvn -q -DskipTests test-compile` 通过。
- `mvn test` 通过，共 22 个 Maven 模块、102 个测试；阶段前基线为 95 个测试。
- `mvn -DskipTests clean package` 通过，生成 app、api-gateway、community-service、ai-service、core-service 共 5 个可执行 JAR。
- app 可执行 JAR 不含 identity、catalog、standards；core-service 可执行 JAR 仅聚合 common、identity、catalog、standards，不含岗位、论坛和 AI 模块。
- 拆分前后源码方法级映射注解均为 149 个，其中包含既有内部测试端点 `/api/knowledge/docs/test`；对外业务端点契约仍为方案所述 148 个。
- 当前沙箱未执行真实端口监听、Nacos 注册及 curl 联通，需按上述清单在具备数据库和 Nacos 的环境复验。

## 10. 回滚

代码回滚必须同时恢复 app 对 identity、catalog、standards 的依赖和 Mapper 配置，删除 app 本地认证适配器，并删除 Gateway 的 `core-api` 路由；仅停止 core-service 会使平台核心路径不可用，不是完整回滚。本阶段没有数据库变更，无需数据回滚。
