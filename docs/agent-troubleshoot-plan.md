# 线上问题自动排查 Agent 实现方案

## 一、目标

基于 AgentScope Java 框架 + DeepAgent 推理思想，在现有 Spring Boot 项目中构建**线上问题自动排查 Agent**：
- 输入：告警信息、异常现象描述
- 输出：根因分析、影响范围、修复建议、自动执行修复操作
- 数据源：内部知识库（Milvus）、监控系统、日志系统
- 核心模型：GLM-4.7（对话推理）+ BGE-large（向量检索）
- Skill/Tool 可插拔：新增排查能力无需改核心代码
- 单一技术栈：全部 Java，集成到现有 Spring Boot 项目

## 二、技术选型

| 组件 | 技术方案 | 说明 |
|------|---------|------|
| Agent 框架 | AgentScope Java v1.0.12+ | Java 98.2%，JDK 17+，Maven 中央仓库 |
| 推理引擎 | ReActAgent（AgentScope 内置） | 自主推理-行动循环，支持 Human-in-the-Loop |
| 推理范式 | DeepAgent 思想 | 统一推理 + 记忆折叠，增强 ReAct 全局视角 |
| 大模型 | GLM-4.7（智谱 API） | OpenAI 兼容格式，AgentScope 自定义 ChatModel 适配 |
| Embedding | Ollama BGE-large（本地） | 向量检索，已有基础设施 |
| 向量库 | Milvus（已有） | 知识库语义检索 |
| 知识库 | MySQL + Milvus（已有） | 文档切片存储 + 向量索引 |
| 记忆系统 | AgentScope Long-term Memory | 内置跨会话持久化 + 语义搜索 |
| 任务规划 | AgentScope PlanNotebook | 内置结构化任务分解 |
| 监控接入 | Prometheus API | 拉取指标数据 |
| 日志接入 | Elasticsearch API / Loki API | 检索日志 |
| Tool 协议 | MCP + Java 原生 Tool | AgentScope 原生支持 MCP，Java Tool 直接注册 |
| 部署 | 现有 Spring Boot 应用 | 无需额外服务，集成到现有部署 |

## 三、架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                  Spring Boot 应用（现有）                      │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              AgentScope Java Agent 层                    │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │  ReActAgent  │  │  记忆系统     │  │  PlanNotebook │  │ │
│  │  │  GLM-4.7     │  │  Long-term   │  │  任务分解     │  │ │
│  │  │  统一推理     │  │  Memory      │  │  步骤追踪     │  │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │ │
│  │         └─────────────────┴─────────────────┘           │ │
│  └─────────────────────────┬───────────────────────────────┘ │
│                            │                                 │
│  ┌─────────────────────────┴───────────────────────────────┐ │
│  │                  Tool 层（可插拔）                        │ │
│  │                                                         │ │
│  │  Java 原生 Tools（Spring Bean）          MCP Tools       │ │
│  │  ┌──────────────┐ ┌──────────────┐    ┌──────────────┐ │ │
│  │  │KnowledgeSearch│ │QueryMetrics  │    │ 外部 MCP     │ │ │
│  │  │SearchLogs     │ │CheckAlerts   │    │ Server       │ │ │
│  │  │QueryTopology  │ │ExecCommand   │    │（可选扩展）   │ │ │
│  │  │CreateTicket   │ │SaveExperience│    │              │ │ │
│  │  └──────────────┘ └──────────────┘    └──────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Skill 管理层                                │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │ │
│  │  │ SkillLoader  │  │ SkillMatcher │  │ SkillRunner  │  │ │
│  │  │ YAML 解析    │  │ 关键词匹配   │  │ 步骤编排执行  │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              已有业务层（复用）                            │ │
│  │  KnowledgeService  VectorStore  EmbeddingService         │ │
│  │  ForumService      ReleaseService  StorageService        │ │
│  └─────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## 四、核心模块设计

### 4.1 推理引擎（AgentScope ReActAgent + DeepAgent 增强）

AgentScope 内置 ReActAgent，结合 DeepAgent 的统一推理思想进行增强：

```java
// Agent 配置
ReActAgent agent = ReActAgent.builder()
    .name("OpsAgent")
    .sysPrompt(OPS_SYSTEM_PROMPT)           // 排查专家系统提示词
    .model(GLM4ChatModel.builder()           // GLM-4.7 模型
        .apiKey(glmApiKey)
        .modelName("glm-4.7")
        .build())
    .tools(tools)                            // 可插拔 Tool 列表
    .memory(longTermMemory)                  // 长期记忆
    .build();
```

DeepAgent 增强点：
- **全局视角**：系统提示词引导 Agent 维持排查全局，不被单步结果绑架
- **并行调用**：ReActAgent 支持一次推理返回多个 Tool 调用
- **记忆折叠**：上下文过长时自动压缩为结构化摘要（自定义 MemoryHook）

### 4.2 记忆系统（AgentScope Long-term Memory + 记忆折叠）

```java
// AgentScope 内置长期记忆
LongTermMemory memory = LongTermMemory.builder()
    .embeddingModel(bgeEmbeddingModel)       // BGE-large 向量化
    .vectorStore(milvusVectorStore)          // Milvus 存储
    .maxEntries(1000)
    .build();

// 记忆折叠 Hook（自定义实现）
public class MemoryFoldingHook implements AgentHook {
    @Override
    public void beforeInference(AgentContext ctx) {
        if (ctx.getMessageCount() > FOLD_THRESHOLD) {
            // 压缩历史消息为结构化摘要
            List<Msg> compressed = foldMemory(ctx.getMessages());
            ctx.setMessages(compressed);
        }
    }
}
```

三层记忆结构：
- **情景记忆**：排查过程的关键事件和决策 → 存入 Long-term Memory
- **工作记忆**：当前排查的中间状态 → AgentScope Session Context
- **工具记忆**：每次 Tool 调用的结果 → 自动记录到对话历史

### 4.3 可插拔 Tool 系统

#### 4.3.1 Java 原生 Tool（推荐，性能最优）

每个 Tool 是一个 Spring Bean，实现 AgentScope 的 Tool 接口：

```java
@Component
public class KnowledgeSearchTool implements Tool {

    private final KnowledgeService knowledgeService;

    @Override
    public String name() {
        return "knowledge_search";
    }

    @Override
    public String description() {
        return "从内部知识库检索相关文档。用于查找排查手册、配置指南、历史案例。参数：query(查询内容), top_k(返回数量)";
    }

    @Override
    public String call(Map<String, Object> params) {
        String query = (String) params.get("query");
        int topK = params.containsKey("top_k") ? (int) params.get("top_k") : 5;

        float[] vector = knowledgeService.embed(query);
        List<VectorStore.VectorSearchResult> results = knowledgeService.search(vector, topK);

        return results.stream()
            .map(r -> "【" + r.getMetadata().get("sourceTitle") + "】\n"
                     + r.getMetadata().get("content")
                     + "\n相关度: " + String.format("%.2f", r.getScore()))
            .collect(Collectors.joining("\n---\n"));
    }
}
```

#### 4.3.2 内置 Tool 列表

| Tool 名称 | 实现方式 | 功能 | 数据源 |
|-----------|---------|------|--------|
| `knowledge_search` | Java Bean | 知识库语义检索 | 复用 KnowledgeService |
| `query_metrics` | Java Bean | 查询监控指标 | Prometheus HTTP API |
| `search_logs` | Java Bean | 搜索应用日志 | Elasticsearch / Loki API |
| `check_alerts` | Java Bean | 查看当前告警 | Alertmanager API |
| `query_topology` | Java Bean | 查询服务拓扑 | CMDB / 注册中心 API |
| `exec_command` | Java Bean（需审批） | 执行运维命令 | SSH / K8s API |
| `create_ticket` | Java Bean | 创建工单 | 工单系统 API |
| `save_experience` | Java Bean | 沉淀排查经验到知识库 | 复用 KnowledgeService |

新增 Tool 只需：写一个 `@Component` 实现 `Tool` 接口，Spring 自动注册。

#### 4.3.3 MCP Tool 扩展（可选）

对于非 Java 的外部工具，通过 MCP 协议接入：

```java
// application.yml
agentscope:
  mcp:
    servers:
      - name: browser
        command: npx
        args: ["@anthropic/mcp-browser"]
      - name: code_interpreter
        command: python
        args: ["tools/code_interpreter.py"]
```

AgentScope Java 原生支持 MCP 协议，可直接连接外部 MCP Server。

### 4.4 Skill 管理系统（自建，2-3 天工作量）

#### 4.4.1 Skill YAML 定义

```yaml
# src/main/resources/skills/oom-troubleshoot.yaml
name: oom-troubleshoot
description: "排查 JVM OOM 问题"
trigger:
  keywords: ["OOM", "OutOfMemoryError", "内存溢出", "heap space"]
steps:
  - tool: query_metrics
    args:
      promql: "process_memory_usage{service='{{service}}'}"
      start: "30m ago"
    description: "查看内存使用趋势"

  - tool: search_logs
    args:
      query: "OutOfMemoryError OR Java heap space OR GC overhead"
      service: "{{service}}"
      timerange: "1h"
    description: "搜索 OOM 相关日志"

  - tool: knowledge_search
    args:
      query: "JVM OOM 排查 heap dump 分析 内存泄漏"
      top_k: 5
    description: "检索知识库中的 OOM 排查经验"

  - tool: query_topology
    args:
      service: "{{service}}"
    description: "查看服务依赖关系"

  - prompt: "综合以上数据，分析 OOM 根因（内存泄漏/GC 配置/堆大小不足），给出修复建议"
```

#### 4.4.2 Skill 加载器

```java
@Component
public class SkillLoader {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        // 扫描 classpath:skills/*.yaml
        Resource[] resources = new PathMatchingResourcePatternResolver()
            .getResources("classpath:skills/*.yaml");
        for (Resource resource : resources) {
            Skill skill = parseYaml(resource);
            skills.put(skill.getName(), skill);
        }
    }

    public Skill match(String input) {
        return skills.values().stream()
            .filter(s -> s.getTrigger().getKeywords().stream()
                .anyMatch(kw -> input.toUpperCase().contains(kw.toUpperCase())))
            .findFirst().orElse(null);
    }

    private Skill parseYaml(Resource resource) {
        // 使用 Jackson YAML 解析
        return new ObjectMapper(new YAMLFactory())
            .readValue(resource.getInputStream(), Skill.class);
    }
}
```

#### 4.4.3 Skill 编排执行器

```java
@Component
public class SkillRunner {

    private final ReActAgent agent;
    private final Map<String, Tool> toolRegistry;

    public String run(Skill skill, Map<String, String> context) {
        StringBuilder accumulated = new StringBuilder();

        for (SkillStep step : skill.getSteps()) {
            if (step.getTool() != null) {
                // 执行 Tool 调用
                Tool tool = toolRegistry.get(step.getTool());
                Map<String, Object> args = resolveArgs(step.getArgs(), context);
                String result = tool.call(args);
                accumulated.append("[").append(step.getDescription()).append("]\n")
                          .append(result).append("\n\n");
            } else if (step.getPrompt() != null) {
                // 将累积数据 + 分析提示词发给 Agent
                String fullPrompt = accumulated + "\n" + resolveArgs(step.getPrompt(), context);
                Msg response = agent.call(Msg.builder()
                    .textContent(fullPrompt)
                    .build()).block();
                return response.getTextContent();
            }
        }
        return accumulated.toString();
    }

    private Map<String, Object> resolveArgs(Map<String, String> template, Map<String, String> context) {
        // {{service}} → 实际值替换
        Map<String, Object> resolved = new HashMap<>();
        template.forEach((k, v) -> {
            String val = v;
            for (Map.Entry<String, String> ctx : context.entrySet()) {
                val = val.replace("{{" + ctx.getKey() + "}}", ctx.getValue());
            }
            resolved.put(k, val);
        });
        return resolved;
    }
}
```

### 4.5 知识库集成（已有基础设施复用）

```
已有：MySQL knowledge_chunks → BGE-large embedding → Milvus
新增：排查经验自动沉淀

排查流程结束 → Agent 总结排查过程 → SaveExperienceTool → 写入 MySQL → 向量化 → Milvus
下次遇到类似问题 → KnowledgeSearchTool 命中历史排查经验
```

知识库数据来源：
- 技术文档（已有）
- 标准操作手册（已有）
- 历史排查记录（新增，Agent 自动沉淀）
- 告警处理 SOP（新增，人工录入或 Agent 生成）

### 4.6 排查流程示例

```
告警：Redis 连接池耗尽，服务 xxx 响应超时

Agent 推理过程：
┌─────────────────────────────────────────────────────────┐
│ Step 1: 理解问题（ReActAgent 推理）                      │
│   - 服务 xxx 出现 Redis 连接池耗尽                       │
│   - 表现为响应超时                                       │
│   - 匹配 Skill: connection-pool-troubleshoot             │
│   - 需要查明：为什么连接池耗尽？是泄漏还是并发过高？      │
├─────────────────────────────────────────────────────────┤
│ Step 2: 执行 Skill 步骤（并行调用多个 Tool）             │
│   ├─ [QueryMetricsTool] Redis 连接数、活跃连接、等待队列 │
│   ├─ [SearchLogsTool] 搜 "Connection pool exhausted"     │
│   ├─ [KnowledgeSearchTool] "Redis 连接池耗尽排查"        │
│   └─ [QueryTopologyTool] 服务 xxx 的 Redis 依赖关系      │
├─────────────────────────────────────────────────────────┤
│ Step 3: 分析结果（Agent 综合推理）                       │
│   - 监控：连接数从 10:00 开始持续上升，10:30 触发上限     │
│   - 日志：发现大量 "connection not returned to pool"      │
│   - 知识库：匹配到"连接泄漏常见原因：未在 finally 关闭"  │
│   - 拓扑：xxx 服务连接了 3 个 Redis 实例                 │
├─────────────────────────────────────────────────────────┤
│ Step 4: 深入排查（Agent 自主发现新线索）                 │
│   - [SearchLogsTool] 搜最近部署记录                      │
│   - [QueryMetricsTool] 对比部署前后的连接数变化           │
│   - 结论：10:00 有一次代码部署，新增了缓存查询未关闭连接  │
├─────────────────────────────────────────────────────────┤
│ Step 5: 输出结论                                         │
│   根因：10:00 部署的 v2.3.1 版本引入连接泄漏 bug         │
│   影响：xxx 服务 Redis 连接池耗尽，所有依赖请求超时       │
│   建议：回滚到 v2.3.0，同时修复连接关闭逻辑              │
│   操作：[ExecCommandTool] 可选自动回滚（Human-in-the-Loop │
│         审批）                                            │
├─────────────────────────────────────────────────────────┤
│ Step 6: 沉淀经验                                         │
│   [SaveExperienceTool] 自动生成知识条目写入知识库         │
└─────────────────────────────────────────────────────────┘
```

## 五、与现有系统集成

### 5.1 Maven 依赖

```xml
<!-- AgentScope Java -->
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope</artifactId>
    <version>1.0.12</version>
</dependency>
```

### 5.2 GLM-4.7 模型适配

AgentScope 支持自定义 ChatModel，适配智谱 API（OpenAI 兼容格式）：

```java
@Component
public class GLM4ChatModel implements ChatModel {

    private final String apiKey;
    private final String baseUrl;
    private final OkHttpClient client = new OkHttpClient();

    public GLM4ChatModel(@Value("${app.ai.api-key}") String apiKey,
                         @Value("${app.ai.base-url}") String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public Msg generate(List<Msg> messages) {
        // 构造 OpenAI 兼容请求
        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4.7");
        body.put("messages", toOpenAIMessages(messages));
        body.put("temperature", 0.1);
        body.put("max_tokens", 8192);

        Request request = new Request.Builder()
            .url(baseUrl + "/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .post(RequestBody.create(
                MediaType.parse("application/json"),
                new Gson().toJson(body)))
            .build();

        // 发送请求，解析响应
        try (Response response = client.newCall(request).execute()) {
            // 解析 OpenAI 格式响应，返回 Msg
        }
    }
}
```

### 5.3 Agent API 控制器

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReActAgent agent;
    private final SkillLoader skillLoader;
    private final SkillRunner skillRunner;

    // 对话式排查
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody AgentChatRequest req) {
        // 1. 匹配 Skill
        Skill skill = skillLoader.match(req.getMessage());

        String response;
        if (skill != null) {
            // 2. 执行 Skill 编排
            response = skillRunner.run(skill, req.getContext());
        } else {
            // 3. 通用推理
            Msg msg = agent.call(Msg.builder()
                .textContent(req.getMessage())
                .build()).block();
            response = msg.getTextContent();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", response);
        result.put("skill", skill != null ? skill.getName() : null);
        return result;
    }

    // 告警 Webhook
    @PostMapping("/webhook/alert")
    public Map<String, Object> alertWebhook(@RequestBody AlertWebhookRequest alert) {
        String service = alert.getService();
        String description = alert.getDescription();

        // 触发排查
        Map<String, String> context = Map.of("service", service);
        Skill skill = skillLoader.match(description);
        String result = skill != null
            ? skillRunner.run(skill, context)
            : agent.call(Msg.builder()
                .textContent("告警：" + description + "，服务：" + service + "，请排查")
                .build()).block().getTextContent();

        return Map.of("status", "completed", "result", result);
    }

    // Skill 管理
    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillLoader.getAll();
    }

    @PostMapping("/skills")
    public void saveSkill(@RequestBody Skill skill) {
        skillLoader.save(skill);
    }

    @DeleteMapping("/skills/{name}")
    public void deleteSkill(@PathVariable String name) {
        skillLoader.delete(name);
    }
}
```

### 5.4 前端扩展（集成到现有 Vue 前端）

在现有 `App.vue` 中新增排查面板：

```
导航栏 → [智能排查] 按钮
  ├─ 对话式排查：输入问题 → Agent 分析 → 展示结论
  ├─ 告警接入：展示最近告警 → 一键触发排查
  ├─ Skill 管理：列表/编辑/新增/删除 Skill
  └─ 排查历史：查看历史排查记录和报告
```

## 六、部署架构

无需额外部署，集成到现有 Spring Boot 应用：

```yaml
# docker-compose.yml（在现有基础上新增 Prometheus 等监控组件）
version: '3.8'

services:
  # 现有后端（已集成 AgentScope Java Agent）
  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - APP_AI_API_KEY=${GLM_API_KEY}
      - APP_AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
    depends_on:
      - mysql
      - milvus

  # 现有 Milvus
  etcd:
    image: quay.io/coreos/etcd:v3.5.18
    command: etcd -listen-client-urls=http://0.0.0.0:2379 -advertise-client-urls=http://0.0.0.0:2379
    ports:
      - "2379:2379"

  milvus:
    image: milvusdb/milvus:latest
    command: milvus run standalone
    ports:
      - "19530:19530"
      - "9091:9091"
    environment:
      - ETCD_ENDPOINTS=etcd:2379
      - COMMON_STORAGETYPE=local
    depends_on:
      - etcd

  # 现有 MySQL
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"

  # 新增：Prometheus（监控数据源）
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
```

## 七、实现路线

### Phase 1：Agent 核心（1.5 周）
- [ ] 引入 AgentScope Java Maven 依赖
- [ ] 实现 GLM-4.7 ChatModel 适配器
- [ ] 配置 ReActAgent + BGE-large Embedding + Milvus 记忆
- [ ] 实现 `KnowledgeSearchTool`（复用现有 KnowledgeService）
- [ ] 实现 `QueryMetricsTool`（对接 Prometheus HTTP API）
- [ ] 实现 `SearchLogsTool`（对接 ES/Loki API）
- [ ] 基础 Agent API（`/api/agent/chat`）

### Phase 2：Skill 系统（1 周）
- [ ] 实现 `SkillLoader`（YAML 解析 + 关键词匹配）
- [ ] 实现 `SkillRunner`（步骤编排 + Tool 调用 + 变量替换）
- [ ] 编写 3-5 个基础排查 Skill YAML
- [ ] 告警 Webhook API（`/api/agent/webhook/alert`）

### Phase 3：前端 + 经验沉淀（1.5 周）
- [ ] 前端智能排查面板（对话式排查 + 排查历史）
- [ ] Skill 管理界面（列表/编辑/新增/删除）
- [ ] `SaveExperienceTool`（排查经验自动沉淀到知识库）
- [ ] 记忆折叠机制（上下文过长时自动压缩）
- [ ] 排查报告自动生成（Markdown 格式）

### Phase 4：检索增强（2 周）
- [ ] **重排序（Reranking）**：粗筛 top 50 → BGE Reranker 精排 → 取 top 5 送给 Agent
- [ ] **查询改写（Query Rewriting）**：用户原始问题 → GLM 改写为多个子查询 → 分别检索 → 合并结果
- [ ] **意图识别（Intent Recognition）**：判断用户输入是排查请求/知识查询/闲聊，路由到不同处理链路
- [ ] **混合检索（Hybrid Retrieval）**：向量检索 + 关键词检索，RRF 融合排序
- [ ] **切片策略优化**：切片大小调整、重叠切片、保留章节上下文前缀

### Phase 5：权限隔离与安全（2 周）
- [ ] **Agent 权限体系**：基于现有 RBAC（8 角色）扩展，不同角色可调用的 Tool 不同
  - 系统管理员：全部 Tool（含 ExecCommand）
  - 运维岗：只读 Tool（查监控/日志/知识库）+ ExecCommand（需审批）
  - 开发经理/运维经理：只读 Tool（查监控/日志/知识库）
  - 其他角色：仅 KnowledgeSearchTool
- [ ] **知识库查询权限隔离**：
  - 敏感文档（安全配置、密码策略等）按角色过滤，查询时注入权限过滤条件
  - Milvus metadata 中增加 `accessLevel` 字段，检索后按角色二次过滤
  - Agent 对话历史按用户隔离，不可跨用户查看
- [ ] **安全过滤（Safety Filter）**：
  - 输入过滤：拦截 Prompt Injection 攻击（检测系统提示词覆盖、角色伪装等模式）
  - 输出过滤：Agent 回复中脱敏（自动识别并遮蔽 IP、密码、密钥、内网地址等）
  - Tool 调用审计：所有 Tool 调用记录持久化，支持事后审计和异常检测
  - 速率限制：单用户/单角色 Agent 调用频率限制，防止滥用
- [ ] **敏感操作二次确认**：
  - ExecCommandTool 执行前生成操作预览，需人工审批（AgentScope Human-in-the-Loop）
  - 高危命令（rm、drop、restart）自动拦截，必须系统管理员审批

### Phase 6：智能进化（持续）
- [ ] `ExecCommandTool` + Human-in-the-Loop 审批
- [ ] 排查准确率统计 + 反馈循环
- [ ] 多 Agent 协作（A2A 协议，复杂问题拆分子任务）
- [ ] Skill 触发条件自动优化（基于历史数据）
- [ ] 排查经验自动沉淀为 Reranker 训练数据，持续优化排序

## 八、关键技术点

### 8.1 为什么选 AgentScope Java

| 维度 | AgentScope Java | QwenPaw (Python) | LangGraph4j |
|------|----------------|-------------------|-------------|
| 语言 | Java，集成到现有项目 | Python，需跨语言调用 | Java |
| 推理引擎 | ReActAgent 内置 | 内置 | 需自己实现 |
| 记忆系统 | Long-term Memory 内置 | 内置 | 需自己实现 |
| MCP 支持 | 原生 | 原生 | 无 |
| A2A 协议 | 原生（Nacos 注册） | CloudPaw 插件 | 无 |
| Human-in-the-Loop | 内置 Hook | 内置工具守卫 | 需自己实现 |
| 部署 | 集成到 Spring Boot | 独立 Python 服务 | 集成到 Spring Boot |
| 生产就绪 | GraalVM + OpenTelemetry | Docker | 需额外配置 |

### 8.2 DeepAgent 思想的落地

DeepAgent 的核心是**统一推理 + 记忆折叠**，在 AgentScope Java 中的落地方式：
- **统一推理**：ReActAgent + GLM-4.7 长上下文，系统提示词引导全局视角
- **记忆折叠**：自定义 AgentHook，上下文超过阈值时压缩为结构化摘要
- **工具动态发现**：Spring Bean 自动注册 + MCP 协议扩展

### 8.3 检索增强技术方案

**重排序（Reranking）**

```
用户问题 → BGE embedding → Milvus 粗筛 top 50
                                    ↓
                          BGE Reranker（交叉编码）
                                    ↓
                          精排 top 5 → 送给 Agent
```

- 模型：`bge-reranker-v2-m3`（本地 Ollama 或远程 API）
- 原理：Embedding 是压缩式表征，信息有损失；Reranker 对问题-文档逐对打分，精度更高
- 实现：新增 `RerankerService`，`KnowledgeSearchTool` 内部调用

**查询改写（Query Rewriting）**

```
用户："Redis 慢"
  ↓ GLM 改写
子查询 1："Redis 慢查询 slowlog 配置排查"
子查询 2："Redis 延迟 latency 分析"
子查询 3："Redis 性能优化 maxmemory-policy"
  ↓ 分别检索
合并结果（去重 + RRF 排序）
```

- 实现：新增 `QueryRewriteService`，用 GLM-4.7 生成 2-3 个子查询
- 适用于：用户问题太短、模糊、或包含多种可能含义

**意图识别（Intent Recognition）**

```
用户输入 → GLM 意图分类 → 路由到不同处理链路
  ├─ 排查请求："Redis 连接池满了" → Agent 排查流程
  ├─ 知识查询："Nginx 配置 keepalive" → 知识库检索直接返回
  ├─ 操作请求："帮我重启 Redis" → ExecCommandTool + 审批
  └─ 闲聊/无关："今天天气" → 礼貌拒绝
```

- 实现：Agent 系统提示词中加入意图分类指令，或用独立轻量模型做分类
- 好处：避免所有请求都走完整 Agent 推理，节省 token 和时间

### 8.4 权限隔离技术方案

**Agent Tool 权限控制**

```java
@Component
public class ToolPermissionFilter {

    private final PermissionService permissionService;

    public List<Tool> filterTools(String username, List<Tool> allTools) {
        Role role = permissionService.getRole(username);
        return allTools.stream()
            .filter(tool -> isAllowed(role, tool))
            .collect(Collectors.toList());
    }

    private boolean isAllowed(Role role, Tool tool) {
        return switch (role) {
            case SYS_ADMIN -> true;  // 全部权限
            case MIDDLEWARE_MGR, DATABASE_MGR, HOST_MGR,
                 NETWORK_MGR, SECURITY_MGR -> isReadOnly(tool) || "exec_command".equals(tool.name());
            case DEV_MGR, OPS_MGR -> isReadOnly(tool);
        };
    }
}
```

**知识库数据级权限隔离**

```java
// KnowledgeSearchTool 中注入权限过滤
public String call(Map<String, Object> params) {
    String query = (String) params.get("query");
    Role role = getCurrentUserRole();

    float[] vector = knowledgeService.embed(query);
    List<VectorSearchResult> results = knowledgeService.search(vector, 50);

    // 按角色过滤敏感文档
    results = results.stream()
        .filter(r -> {
            String accessLevel = r.getMetadata().get("accessLevel");
            return accessLevel == null
                || "public".equals(accessLevel)
                || role == Role.SYS_ADMIN
                || (role.isManager() && !"confidential".equals(accessLevel));
        })
        .limit(topK)
        .collect(Collectors.toList());

    return formatResults(results);
}
```

### 8.5 安全过滤技术方案

**输入过滤（Prompt Injection 防护）**

```java
@Component
public class InputSafetyFilter {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore.*previous.*instructions"),
        Pattern.compile("(?i)you\\s+are\\s+now"),
        Pattern.compile("(?i)system\\s*prompt"),
        Pattern.compile("(?i)forget.*rules"),
        Pattern.compile("(?i)act\\s+as\\s+if")
    );

    public SafetyCheckResult check(String input) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return SafetyCheckResult.blocked("检测到潜在的 Prompt Injection 攻击");
            }
        }
        return SafetyCheckResult.passed();
    }
}
```

**输出过滤（敏感信息脱敏）**

```java
@Component
public class OutputSafetyFilter {

    private static final List<Pattern> SENSITIVE_PATTERNS = List.of(
        Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),           // IP 地址
        Pattern.compile("(?i)(password|passwd|secret|token|key)\\s*[:=]\\s*\\S+"),  // 密码/密钥
        Pattern.compile("-----BEGIN.*PRIVATE KEY-----"),                // 私钥
        Pattern.compile("\\b[A-Za-z0-9+/]{40,}={0,2}\\b")             // Base64 密钥
    );

    public String filter(String output) {
        String filtered = output;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            filtered = pattern.matcher(filtered).replaceAll(m -> {
                String matched = m.group();
                if (matched.length() <= 8) return "***";
                return matched.substring(0, 4) + "***" + matched.substring(matched.length() - 4);
            });
        }
        return filtered;
    }
}
```

## 九、当前修复计划（2026-06-07）

### 9.1 本轮 P0 修复范围

1. **会话一致性**
   - 修复 Ops Agent 首次回复时用 `insert` 更新标题导致的会话分裂。
   - RAG 和 Ops 写入前必须校验会话存在、归属和模式。

2. **会话用户隔离**
   - `chat_sessions` 增加创建人字段。
   - 会话列表、会话详情、聊天写入、模式切换均按当前用户过滤。
   - 系统管理员可作为审计角色查看全部会话，普通用户只能查看自己的会话。

3. **Wiki/RAG 权限传递**
   - SSE 异步线程不再从 `SecurityContextHolder` 隐式取用户。
   - Controller 显式传入 `Authentication`，避免线程池复用导致权限上下文丢失。

4. **Skill 管理权限**
   - Skill 新增、编辑、删除、保存经验只允许系统管理员或专业管理员操作。
   - 普通登录用户只允许查看 Skill/Tool 列表和发起排查。

### 9.2 后续 P1/P2 修复方向

- 增加 `agent_tool_calls` 审计表，记录工具名、参数摘要、调用人、耗时、结果状态。
- 引入 ToolGateway，统一做权限校验、参数 schema、超时、脱敏和错误收敛。
- 将“保存经验”从直接写 Skill YAML 调整为生成 LLM Wiki `EXPERIENCE` DRAFT 页面，审核通过后再参与检索。
- 增加 Agent 状态机：`PLANNING / WAITING_FOR_PARAMS / RUNNING_TOOL / NEED_APPROVAL / SUMMARIZING / COMPLETED / FAILED / CANCELLED`。

### 9.3 本轮验收标准

- 普通用户无法读取或写入他人的排查会话。
- RAG Agent 调用 Wiki 搜索时使用当前请求用户权限。
- Ops Agent 首轮回答后刷新页面，问答仍在同一个会话下。
- 普通用户调用 Skill 写接口返回 403。
- 后端编译通过，关键 Controller/Service 单元测试覆盖新增权限逻辑。
