# 自动排查 Agent 架构设计

## Context

当前系统是 RAG 对话模式：用户提问 → 检索知识库 → LLM 回答。后续需要升级为**自动排查 Agent**：Agent 能主动调用工具（查日志、查监控、执行命令）来诊断问题，而不只是被动回答。

## 两种实现路径对比

### 路径 A：LangChain4j Tools（推荐）

LangChain4j 原生支持 `@Tool` 注解，将 Java 方法注册为 LLM 可调用的工具。

```
用户: "Redis 连接超时"
  → LLM 决定调用工具: queryLogs("redis", "timeout", "1h")
  → 工具返回日志片段
  → LLM 继续推理: checkMetrics("redis", "cpu,memory,connections")
  → 工具返回监控数据
  → LLM 给出诊断结论
```

**优点：**
- LangChain4j 内置支持，代码量少
- LLM 自主决定调用哪个工具、传什么参数
- 工具用 `@Tool` 注解定义，开发简单
- 支持多轮工具调用（LLM 自动规划）

**缺点：**
- 依赖 LLM 的 function calling 能力（MiMo 支持）
- 工具调用结果的质量取决于 LLM 的推理能力

### 路径 B：预定义 Skill 流程

固定排查流程：先查监控 → 再查日志 → 再查知识库 → 输出结论。

**优点：** 流程可控，不依赖 LLM 推理
**缺点：** 灵活度低，不同问题需要不同流程

## 推荐方案：LangChain4j Tools

### 核心架构

```
TroubleshootAgent（已有）
  ├─ ChatModel（LangChain4j，已有）
  ├─ KnowledgeSearchTool（已有，RAG 检索）
  ├─ MonitorQueryTool（新增，查监控）
  ├─ LogQueryTool（新增，查日志）
  ├─ AutomationTool（新增，执行自动化）
  └─ ServerCommandTool（新增，SSH 执行命令）
```

### 工具定义示例

```java
@Component
public class MonitorQueryTool {

    @Tool("查询监控系统的指标数据。参数：service(服务名), metrics(指标，逗号分隔), duration(时间范围如1h/24h)")
    public String queryMetrics(String service, String metrics, String duration) {
        // 调用 Prometheus/Grafana API
        // 返回指标数据摘要
    }

    @Tool("查询服务的告警信息。参数：service(服务名), severity(告警级别：critical/warning/info)")
    public String queryAlerts(String service, String severity) {
        // 调用告警系统 API
        // 返回活跃告警列表
    }
}

@Component
public class LogQueryTool {

    @Tool("查询日志系统中的日志。参数：service(服务名), keyword(关键词), duration(时间范围), level(日志级别：ERROR/WARN/INFO)")
    public String queryLogs(String service, String keyword, String duration, String level) {
        // 调用 ELK/Loki API
        // 返回匹配的日志片段
    }
}

@Component
public class AutomationTool {

    @Tool("重启指定服务。参数：service(服务名), instance(实例IP，可选)")
    public String restartService(String service, String instance) {
        // 调用自动化平台 API
        // 返回执行结果
    }

    @Tool("执行只读诊断命令（不允许写操作）。参数：host(目标主机), command(命令)")
    public String runDiagnosticCommand(String host, String command) {
        // SSH 执行命令，限制为只读操作
        // 返回命令输出
    }
}
```

### Agent 编排

```java
@Service
public class TroubleshootAgent {

    private final ChatModel chatModel;
    private final KnowledgeService knowledgeService;
    // 工具通过 LangChain4j 自动注册
    private final List<Object> tools;  // Spring 自动注入所有 @Tool Bean

    public AgentResponse chat(Long sessionId, String userMessage) {
        // 1. 检索知识库（已有）
        List<SearchResult> results = knowledgeService.search(userMessage, 5);

        // 2. 构建消息
        List<ChatMessage> messages = buildMessages(sessionId, userMessage, results);

        // 3. 调用 LLM（带工具）
        // LangChain4j 自动处理工具调用循环：
        // LLM 返回 tool_calls → 执行工具 → 将结果发回 LLM → 重复直到 LLM 给出最终答案
        ChatResponse response = chatModel.chat(messages, tools);

        // 4. 返回结果
        return new AgentResponse(response.aiMessage().text(), references);
    }
}
```

### 实施顺序

| 阶段 | 内容 | 前置条件 |
|------|------|----------|
| P0 | 对话问答（当前已完成） | 无 |
| P1 | KnowledgeSearchTool 优化（结构化检索） | 无 |
| P2 | MonitorQueryTool（对接监控系统） | 提供监控系统 API 文档 |
| P3 | LogQueryTool（对接日志系统） | 提供日志系统 API 文档 |
| P4 | AutomationTool（对接自动化平台） | 提供自动化平台 API 文档 |
| P5 | ServerCommandTool（SSH 诊断） | 配置 SSH 密钥 |

### 需要你提供的信息

1. **监控系统**：Prometheus? Grafana? Zabbix? API 地址和认证方式？
2. **日志系统**：ELK? Loki? 自研? API 地址？
3. **自动化平台**：自研? Ansible Tower? Jenkins? API 文档？
4. **服务器访问**：是否允许 Agent SSH 到目标机器执行命令？

## 安全设计

- 所有工具操作记录审计日志
- 写操作（重启服务、修改配置）必须人工确认
- 只读操作（查日志、查监控）可自动执行
- SSH 命令限制为只读（ps, top, df, cat, grep 等）
