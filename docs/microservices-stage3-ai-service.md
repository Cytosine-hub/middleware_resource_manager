# 后端微服务化阶段 3：ai-service 剥离说明

## 1. 阶段结果与拓扑

本阶段把 knowledge、wiki、ops-agent 作为一个紧耦合的 AI/Agent 边界上下文，从 app 整体剥离到独立 Spring Boot 应用 `ai-service`。三者继续在同一进程内直接调用，不增加内部 HTTP/RPC，也不改 Controller 路径、鉴权规则、SQL、数据库 schema 或 DML。

```text
Vue / Vite (:5173)
        |
        v
api-gateway (:8080)
        |-- /api/forum/** --------------------------> community-service (:8082)
        |-- /api/knowledge/** ----------------------|
        |-- /api/agent/** --------------------------|
        |-- /api/wiki/** ---------------------------+--> ai-service (:8083)
        |-- /api/ops-agent/** ----------------------|
        |-- /api/ops-agent/export/** ---------------|
        `-- 其余 /api/**、/files/** ----------------> app (:8081)
```

默认 profile 使用静态地址，四个进程均关闭 Nacos。`cloud` profile 下四个进程注册 Nacos，Gateway 分别使用 `lb://community-service`、`lb://ai-service` 和 `lb://infra-portal-app`。

## 2. 构建与模块边界

四个可执行产物：

```text
backend/app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
backend/api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
backend/community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
backend/ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
```

依赖方向固定为：

```text
knowledge
   ^
   |
 wiki
   ^
   |
ops-agent
   ^
   |
ai-service（同时显式依赖 knowledge、wiki、ops-agent）
```

阶段 0 的 `modular-monolith-parent` 曾把 Milvus、LangChain4j、Tika、POI、Gson、OkHttp、JGraphT 和 YAML 等依赖注入所有旧模块。阶段 3 将这些依赖下沉到实际使用模块，app 不再通过父 POM携带 Milvus、LangChain4j、JGraphT 或 Agent YAML 解析器。

knowledge 与 wiki 原有少量双向源码引用无法用 Maven 循环依赖表达。为保持方法体和业务行为不变，跨 knowledge/wiki 的 `KnowledgeService`、`TroubleshootAgent` 及其两个 Controller 原样迁入 ai-service；wiki 显式依赖 knowledge，ops-agent 显式依赖二者。原 knowledge/wiki/ops-agent 测试也全部迁入 ai-service 执行。

说明：ops-agent 的 Excel 导出所需 POI 已归 ai-service；app 中 identity 的用户 Excel 和 standards 的文档转换仍有独立 POI/Tika 需求，因此这些非 AI 功能所需依赖不能在不改变既有端点的前提下删除。

## 3. Profile 与路由

| 组件/路由 | 默认 profile | `cloud` profile |
|---|---|---|
| Gateway | `:8080`，Nacos 关闭 | 注册为 `api-gateway` |
| app | `:8081`，Nacos 关闭 | 注册为 `infra-portal-app` |
| community-service | `:8082`，Nacos 关闭 | 注册为 `community-service` |
| ai-service | `:8083`，Nacos 关闭 | 注册为 `ai-service` |
| `/api/forum/**` | `${COMMUNITY_SERVICE_URL:http://127.0.0.1:8082}` | `lb://community-service` |
| AI/Agent 五组原路径 | `${AI_SERVICE_URL:http://127.0.0.1:8083}` | `lb://ai-service` |
| 其余 `/api/**`、`/files/**` | `${APP_SERVICE_URL:http://127.0.0.1:8081}` | `lb://infra-portal-app` |

`community-api` 和 `ai-api` 均声明在 app 泛路由之前，不剥离、不增加也不重写路径。ai-service 的 `cloud` profile 可选导入：

```text
ai-service.properties
```

四个进程必须使用相同的 `NACOS_NAMESPACE` 和 `NACOS_DISCOVERY_GROUP`。

## 4. 数据与认证归属

一期继续共享 `middleware_resource_manager` MySQL 库，不执行数据迁移。

ai-service 逻辑拥有：

- `knowledge_chunks`
- `chat_sessions`
- `chat_messages`
- `wiki_*`
- `agent_tool_invocations`

认证复用 `common-security`。`TokenAuthenticationFilter` 读取共享库 `user_tokens`，再通过 `admin_accounts` 和 `roles` 恢复用户、角色和岗位权限；Bearer Token 格式、滑动续期和 `PermissionService` 语义不变。ai-service 的 MyBatis 配置使用白名单，只加载认证读取与 AI 集群所需的既有 Mapper XML。

## 5. 配置项

数据库配置继续使用 `APP_DB_HOST`、`APP_DB_PORT`、`APP_DB_NAME`、`APP_DB_USERNAME`、`APP_DB_PASSWORD`，三个业务服务应指向同一共享库。

AI/Agent 配置从 app 移到 ai-service，环境变量名保持不变：

- LLM：`AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL`、`AI_MAX_TOKENS`、`AI_TEMPERATURE`、`AI_TIMEOUT`
- Embedding：`EMBEDDING_BASE_URL`、`EMBEDDING_API_KEY`、`EMBEDDING_MODEL`
- Milvus：`VECTOR_TYPE`、`VECTOR_HOST`、`VECTOR_PORT`、`VECTOR_COLLECTION`
- Wiki：`WIKI_*`
- Zabbix：`ZABBIX_URL`、`ZABBIX_USERNAME`、`ZABBIX_PASSWORD`、`ZABBIX_TIMEOUT`
- Skill：`SKILLS_DIR`
- Nacos：`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_NAMESPACE`、`NACOS_CONFIG_GROUP`、`NACOS_DISCOVERY_GROUP`

启动 ai-service 前至少应设置 `APP_DB_PASSWORD`、`AI_API_KEY` 和 `WIKI_EXPORT_SIGNATURE_SECRET`；使用 Zabbix 工具时还应设置 `ZABBIX_PASSWORD`。仓库配置不提供真实凭据的默认值。

## 6. 默认 profile 多进程运行

```bash
cd backend
mvn -DskipTests clean package

java -jar app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
java -jar community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
java -jar ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

四条 `java` 命令需要在不同终端运行。Windows 可执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-local-full.ps1
```

也可独立启动 app 与 ai-service，二者默认 profile 均不访问 Nacos：

```bash
cd backend
mvn -pl app -am spring-boot:run
mvn -pl ai-service -am spring-boot:run
```

## 7. 默认 profile 验证清单

先通过 app 登录接口取得 `TOKEN`，再执行：

```bash
# app 保留非 AI API
curl -i http://127.0.0.1:8081/api/public/config

# 带有效 Token 直连 app 的 AI 路径应无 Controller，返回 404
curl -i http://127.0.0.1:8081/api/wiki/pages -H "Authorization: Bearer $TOKEN"

# ai-service 继续执行原鉴权；无 Token 返回 401
curl -i http://127.0.0.1:8083/api/wiki/pages

# 直连和经 Gateway 的原路径均保持不变
curl -i http://127.0.0.1:8083/api/wiki/pages -H "Authorization: Bearer $TOKEN"
curl -i http://127.0.0.1:8080/api/wiki/pages -H "Authorization: Bearer $TOKEN"
curl -i http://127.0.0.1:8080/api/ops-agent/sessions -H "Authorization: Bearer $TOKEN"
curl -i http://127.0.0.1:8080/api/forum/posts
curl -i http://127.0.0.1:8080/api/public/config
```

验收时还需核对上传、Wiki 导入/导出、Agent SSE、Zabbix Excel 导出，确认请求体、响应状态、响应头和流式输出与拆分前一致。

## 8. `cloud` profile 验证清单

准备 Nacos 3.x，为四个终端设置相同 Nacos 环境变量，然后启动：

```bash
java -Dspring.profiles.active=cloud -jar app/target/infra-portal-0.0.1-SNAPSHOT-exec.jar
java -Dspring.profiles.active=cloud -jar community-service/target/community-service-0.0.1-SNAPSHOT-exec.jar
java -Dspring.profiles.active=cloud -jar ai-service/target/ai-service-0.0.1-SNAPSHOT-exec.jar
java -Dspring.profiles.active=cloud -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT-exec.jar
```

检查 Nacos 中四个服务各有健康实例，端口为 8081、8082、8083、8080。停止 ai-service 后，AI 五组路径应不可用，但论坛和 app 路由仍正常；恢复 ai-service 后，Gateway 应随 Nacos 健康实例自动恢复。

## 9. 自动化验收

```bash
cd backend
mvn -q -DskipTests test-compile
mvn test
mvn -DskipTests clean package
```

阶段 3 新增或强化的契约：

| TC | 验证点 |
|---|---|
| `TC-APP-003` | app 类路径不含 knowledge/wiki/ops-agent Controller |
| `TC-APP-004` | app 类路径不含 Milvus、LangChain4j、JGraphT、Agent YAML 解析器 |
| `TC-AI-001` | ai-service 默认 profile 独立上下文、8083、Nacos 关闭、六个 Controller 存在 |
| `TC-AI-002` | 五组 AI/Agent 路径继续要求 Bearer Token |
| `TC-GATEWAY-001` | 默认静态 AI 路由指向 8083 |
| `TC-GATEWAY-002` | cloud AI 路由使用 `lb://ai-service` |

当前沙箱若禁止监听端口，只执行构建、上下文测试、JAR 和依赖检查；真实进程、Nacos、Milvus、LLM、Zabbix 与 curl 联通按本清单在外部环境复验。

### 9.1 本地执行结果（2026-07-20）

- `mvn -q -DskipTests test-compile`：通过。
- `mvn test`：21 个 Reactor 模块全部 `BUILD SUCCESS`，合计 95 项测试，0 失败、0 错误、0 跳过；阶段 2 为 91 项，本阶段未减少测试。
- `mvn -DskipTests clean package`：通过，从空 `target` 完整编译并产出 4 个 `*-exec.jar`。
- JAR 内容检查：app 不含 knowledge/wiki/ops-agent 模块及 Milvus、LangChain4j、JGraphT、Agent YAML 解析器；ai-service 包含三个业务模块及对应 AI/导出依赖。
- 迁移一致性检查：4 个跨 knowledge/wiki 编排类与迁移前 Git 版本逐字一致；15 个原 AI 测试仅移动目录，断言未改。
- 当前沙箱未执行真实端口监听、Nacos 注册、Milvus/LLM/Zabbix 联通和 curl 验证，需按第 7、8 节在目标环境复验。

## 10. 回滚

代码回滚必须同时恢复 app 对 knowledge/wiki/ops-agent 的依赖与 AI 配置，并删除 Gateway 的 `ai-api` 路由。仅停止 ai-service 会使五组 AI/Agent 路径不可用，不是完整回滚。阶段 3 没有数据库变更，无需数据回滚。
