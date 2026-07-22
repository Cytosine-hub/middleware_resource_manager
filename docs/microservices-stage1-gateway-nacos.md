# 后端微服务化阶段 1：API Gateway + Nacos

## 1. 阶段边界与拓扑

本阶段不拆业务服务，不改变 Controller、Service、Mapper、SQL、数据库结构、鉴权语义或 API 契约。业务仍由 `app` 单体进程提供，只在其前方新增独立的 Spring Cloud Gateway，并为两个进程接入 Nacos 注册中心和配置中心。

```text
Vue / Vite (:5173)
        |
        | /api/**、/files/**
        v
api-gateway (:8080, service=api-gateway)
        |
        | 默认 profile: http://127.0.0.1:8081
        | cloud profile: lb://infra-portal-app (Nacos)
        v
app 单体 (:8081, service=infra-portal-app)
        |
        v
MySQL / Milvus / LLM / Zabbix（保持原有连接方式）
```

`/files/**` 与 `/api/**` 使用同一条路由，避免 app 改到 `8081` 后下载地址失效。Gateway 不做鉴权或改写路径，原 `SecurityConfig` 和 Bearer Token 处理仍全部由 app 执行。

## 2. 版本与构建结构

| 组件 | 版本 | 选择依据 |
|---|---:|---|
| Spring Boot | `3.5.3` | 项目既有版本 |
| Spring Cloud | `2025.0.0` | `2025.0.x` 官方兼容 Spring Boot `3.5.x` |
| Spring Cloud Alibaba | `2025.0.0.0` | 官方矩阵对应 Spring Cloud `2025.0.0`、Spring Boot `3.5.x` |
| Nacos Client | `3.0.3` | 由 Alibaba BOM 管理 |
| Nacos Server | `3.x`，验证建议 `3.2.3` | Alibaba `2025.0.x` 使用 public/空 namespace 时要求 Nacos Server 3.x |
| JDK | `17` | 两条 2025.0.x 版本线的最低要求 |

版本只在 `backend/pom.xml` 的 `dependencyManagement` 中统一导入：

- `org.springframework.cloud:spring-cloud-dependencies:2025.0.0`
- `com.alibaba.cloud:spring-cloud-alibaba-dependencies:2025.0.0.0`

阶段 0 根 POM 的业务依赖原本会被所有子模块继承，导致 WebFlux Gateway 同时带入 Servlet/MyBatis/MySQL/AI 依赖。现将这些既有依赖原样移到 `modular-monolith-parent`；原 16 个单体模块继承该父 POM，`api-gateway` 直接继承根父 POM，因此两个可执行应用的依赖边界互不污染。

构建产物：

```text
backend/app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

## 3. Profile 行为

| 行为 | 默认 profile | `cloud` profile |
|---|---|---|
| app 端口 | `8081` | `8081` |
| Gateway 端口 | `8080` | `8080` |
| app 注册 Nacos | 关闭 | 开启 |
| Gateway 注册/发现 Nacos | 关闭 | 开启 |
| Nacos Config | 关闭且跳过 import check | 开启，使用 `spring.config.import` |
| Gateway 到 app | 静态 `http://127.0.0.1:8081` | 动态 `lb://infra-portal-app` |

默认 profile 明确设置：

```yaml
spring.cloud.nacos.discovery.enabled: false
spring.cloud.nacos.discovery.register-enabled: false
spring.cloud.nacos.config.enabled: false
spring.cloud.nacos.config.import-check.enabled: false
```

因此无 Nacos 时 app 上下文测试和阶段 0 单体启动路径不应访问注册/配置中心。默认 Gateway 的静态路由用于本地兼容；只有激活 `cloud` 才启用 Nacos 和动态服务发现。

`cloud` profile 从 Nacos 的 `DEFAULT_GROUP` 导入以下可选 DataId：

```text
infra-portal-app.properties
api-gateway.properties
```

DataId 不存在时使用 JAR 内默认值；存在时支持刷新。`NACOS_CONFIG_GROUP`、`NACOS_DISCOVERY_GROUP` 和 `NACOS_NAMESPACE` 可覆盖默认隔离范围。

## 4. 配置项

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 客户端地址 |
| `NACOS_USERNAME` | 空 | Nacos 客户端用户名；开启鉴权时必须注入 |
| `NACOS_PASSWORD` | 空 | Nacos 客户端密码；开启鉴权时必须注入 |
| `NACOS_NAMESPACE` | 空（public） | 注册与配置 namespace ID |
| `NACOS_CONFIG_GROUP` | `DEFAULT_GROUP` | 配置分组 |
| `NACOS_DISCOVERY_GROUP` | `DEFAULT_GROUP` | 服务分组；app 和 Gateway 必须一致 |
| `APP_SERVICE_URL` | `http://127.0.0.1:8081` | 仅默认 profile 的静态兜底地址 |

岗位前端仍默认使用相对地址 `/api`，由 Vite 或 Nginx 反代到 Gateway `:8080`：

```text
VITE_MIDDLEWARE_API_BASE_URL=/api
VITE_DATABASE_API_BASE_URL=/api
VITE_HOST_API_BASE_URL=/api
VITE_NETWORK_API_BASE_URL=/api
VITE_NETWORK_SECURITY_API_BASE_URL=/api
```

前后端跨域部署时，这些值应填写 Gateway 地址，例如 `https://portal.example.com/api`，不得指向 app `:8081`。本阶段不需要修改任何前端岗位模块代码。

## 5. 无 Nacos 的本地启动

先构建两个 JAR，再使用默认 profile 启动 app、Gateway 和前端：

```bash
cd backend
mvn -DskipTests clean package
java -jar app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

Windows 可在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

验证 `curl http://127.0.0.1:8080/api/public/config`。此路径经 Gateway 静态转发到 `8081`，不连接 Nacos。

## 6. Nacos 联通验证清单

当前执行沙箱禁止监听 TCP 端口，不能在此完成本节运行时验证。请在允许 Docker、端口监听和访问 Maven Central 的环境逐项执行。

### 6.1 启动测试用 Nacos 3

下面使用 Nacos `3.2.3` 单机模式。Nacos 3 控制台容器端口 `8080` 映射到宿主机 `18080`，避免与 Gateway 冲突；`8848` 和 `9848` 分别供客户端 HTTP 与 gRPC 使用。

```bash
export NACOS_AUTH_TOKEN="$(openssl rand -base64 48)"
export NACOS_AUTH_IDENTITY_KEY="serverIdentity"
export NACOS_AUTH_IDENTITY_VALUE="$(openssl rand -hex 16)"

docker run --name nacos-stage1 \
  -e MODE=standalone \
  -e NACOS_AUTH_ENABLE=true \
  -e NACOS_AUTH_TOKEN="$NACOS_AUTH_TOKEN" \
  -e NACOS_AUTH_IDENTITY_KEY="$NACOS_AUTH_IDENTITY_KEY" \
  -e NACOS_AUTH_IDENTITY_VALUE="$NACOS_AUTH_IDENTITY_VALUE" \
  -p 18080:8080 -p 8848:8848 -p 9848:9848 \
  -d nacos/nacos-server:v3.2.3

docker logs -f nacos-stage1
```

日志出现 `Nacos started successfully` 后访问 `http://127.0.0.1:18080`，初始化 `nacos` 管理员密码。将实际密码放入后续进程的 `NACOS_PASSWORD`，不要把测试默认值用于生产。

### 6.2 准备可选配置

在 Nacos 控制台的 public namespace、`DEFAULT_GROUP` 下创建：

```properties
# DataId: infra-portal-app.properties
logging.level.com.middleware.manager=INFO
```

```properties
# DataId: api-gateway.properties
logging.level.com.middleware.gateway=INFO
```

这一步验证配置中心导入路径；不创建 DataId 也能使用 JAR 内默认值启动。

### 6.3 构建并启动 app

```bash
cd backend
mvn -DskipTests clean package

export NACOS_SERVER_ADDR=127.0.0.1:8848
export NACOS_USERNAME=nacos
export NACOS_PASSWORD='<控制台初始化后的密码>'

java -Dspring.profiles.active=cloud \
  -jar app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
```

检查 app 日志没有 Nacos 注册异常；在 Nacos 服务列表确认 `infra-portal-app` 有一个健康实例，端口为 `8081`。

### 6.4 启动 Gateway

另开终端，使用相同 Nacos 环境变量：

```bash
cd backend
java -Dspring.profiles.active=cloud \
  -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

在 Nacos 服务列表确认 `api-gateway` 健康实例端口为 `8080`，再执行：

```bash
curl -i http://127.0.0.1:8080/api/public/config
curl -i http://127.0.0.1:8080/api/public/releases
```

验收标准：Gateway 返回 app 的原始 HTTP 状态、响应头和 JSON，路径没有被剥离；带 Bearer Token 的既有接口鉴权行为与直连 `8081` 一致。

### 6.5 故障与动态性验证

1. 停止 app，Gateway 同一路径应返回服务不可用，而不是路由到固定 `localhost:8081`。
2. 重启 app，Nacos 实例恢复健康后 Gateway 请求应自动恢复。
3. 修改两个 DataId 的日志级别，观察对应进程收到配置变更；业务关键配置是否允许动态刷新需逐项评估，不在本阶段扩展。
4. 核对 `/files/{token}` 经 `8080` 下载仍保留原鉴权、Content-Disposition 和文件内容。
5. 生产必须使用 Nacos 集群、开启鉴权、隔离 namespace，并限制 Nacos 只在内网可达。

## 7. 构建与当前沙箱结果

阶段 1 改动前的基线已执行：

```text
mvn -q -DskipTests test-compile  -> SUCCESS
mvn test                         -> SUCCESS，83 tests
```

引入 BOM 后，本沙箱的 Maven 默认仓库 `~/.m2` 为只读；切换到 `/tmp` 可写仓库后，终端网络又无法解析 `repo.maven.apache.org`。因此新 Cloud/Alibaba 构件无法在本沙箱首次下载，最终 `test-compile`、83 项回归测试和 `clean package` 的联网构建仍需在上述环境执行。失败发生在 Maven 模型解析/依赖下载阶段，尚未进入 Java 编译或测试。

推荐版本组合已经按官方兼容矩阵固定，未改用旧缓存中的 Spring Cloud `2021.0.5` 硬凑。联网环境的验收命令为：

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

预期回归测试至少为原 83 项加 Gateway 的 `TC-GATEWAY-001`、`TC-GATEWAY-002`，全部失败数和错误数均为 0。

## 8. 回滚

不激活 `cloud` 即关闭 Nacos；Gateway 默认改走 `APP_SERVICE_URL` 静态地址。紧急情况下也可让调用方临时直连 app `:8081`。本阶段没有数据库迁移和业务代码改动，回滚不涉及数据处理。

## 9. 官方参考

- [Spring Cloud 与 Spring Boot 版本兼容矩阵](https://spring.io/projects/spring-cloud/)
- [Spring Cloud Alibaba 2025.x 版本说明](https://sca.aliyun.com/docs/2025.x/overview/version-explain/)
- [Spring Cloud Alibaba Nacos 2025.x 快速开始](https://sca.aliyun.com/docs/2025.x/user-guide/nacos/quick-start/)
- [Spring Cloud Gateway 4.3 WebFlux Starter](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html)
- [Nacos 3 Docker 快速开始](https://nacos.io/en/docs/latest/quickstart/quick-start-docker/)
