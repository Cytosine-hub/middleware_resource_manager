# 知识库 & 智能排查模块投产手册

## 一、环境要求

| 组件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | Temurin 17 推荐 |
| MySQL | 8.0+ | 现有数据库，复用 |
| Milvus | 2.4+ | 向量数据库，可选（不部署则使用内存向量库） |
| LLM API | OpenAI 兼容格式 | 需要 API Key（当前使用 mimo-v2.5-pro） |
| Embedding 模型 | OpenAI 兼容格式 | 本地 Ollama BGE-large（localhost:11434）或远程 API |

## 二、部署架构

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   前端 Nginx  │────▶│  Spring Boot │────▶│    MySQL    │
│   Vue 3 SPA  │     │  Java 17     │     │             │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    │             │
              ┌─────▼─────┐ ┌────▼─────┐
              │  LLM API   │ │  Milvus   │
              │ 对话+嵌入   │ │  向量库    │
              └───────────┘ └──────────┘
```

## 三、部署步骤

### 3.1 部署 Milvus

```bash
# Docker 部署（推荐）
docker run -d --name milvus \
  -p 19530:19530 \
  -p 9091:9091 \
  -v /data/milvus:/var/lib/milvus \
  --restart always \
  milvusdb/milvus:v2.4-latest \
  milvus run standalone

# 验证
curl http://localhost:9091/healthz
# 返回 {"status":"healthy"} 即成功
```

### 3.2 获取 LLM API Key 与 Embedding 模型

当前项目使用 OpenAI 兼容格式的 LLM API。对话模型和 Embedding 模型可分开配置。

**本地开发（local profile）**：Embedding 使用本地 Ollama BGE-large 模型：

```yaml
# application-local.yml
langchain4j:
  open-ai:
    embedding-model:
      base-url: http://localhost:11434/v1
      api-key: ollama
      model-name: bge-large
```

前置条件：安装 Ollama 并下载 bge-large 模型：
```bash
ollama pull bge-large
```

**生产环境**：在 `application.yml` 或环境变量中配置：

```yaml
langchain4j:
  open-ai:
    chat-model:
      base-url: https://your-llm-api/v1
      api-key: your-api-key
      model-name: your-model-name
    embedding-model:
      base-url: https://your-embedding-api/v1
      api-key: your-api-key
      model-name: bge-large   # 或其他 embedding 模型，维度需匹配 VECTOR_DIM=1024
```

也可通过环境变量覆盖（见 3.4 节）。

### 3.3 配置数据库

```sql
-- 创建知识库表（首次部署执行）
CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT NOT NULL COMMENT '切片文本内容',
    source_title VARCHAR(500) COMMENT '来源文档标题',
    source_type VARCHAR(50) COMMENT '来源类型：STANDARD_DOC / UPLOAD',
    source_id BIGINT COMMENT '来源文档ID',
    category VARCHAR(80) COMMENT '分类',
    software VARCHAR(120) COMMENT '软件名称',
    chunk_index INT DEFAULT 0 COMMENT '切片在文档中的序号',
    vector_id VARCHAR(100) COMMENT '向量存储ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_source (source_type, source_id),
    INDEX idx_category (category),
    INDEX idx_software (software)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文本切片';

CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) COMMENT '会话标题',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话会话';

CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '会话ID',
    role VARCHAR(20) NOT NULL COMMENT '角色：user / assistant / system',
    content TEXT NOT NULL COMMENT '消息内容',
    references_text TEXT COMMENT '引用的知识来源JSON',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息';
```

### 3.4 配置应用

创建 `application-prod.yml`（已有），设置环境变量：

```bash
# LLM API（在 application.yml 中配置，或通过环境变量覆盖）
# LangChain4j 配置见 application.yml 的 langchain4j 节点

# 向量数据库（可选，不设置则使用内存向量库）
export VECTOR_TYPE=milvus
export VECTOR_HOST=milvus-server-ip
export VECTOR_PORT=19530

# 数据库连接
export APP_DB_HOST=mysql-server-ip
export APP_DB_PORT=3306
export APP_DB_NAME=middleware_resource_manager
export APP_DB_USERNAME=app_user
export APP_DB_PASSWORD=app_password
```

### 3.5 编译打包

```bash
JAVA_HOME=/path/to/jdk17 mvn clean package -DskipTests -Pprod
```

### 3.6 启动应用

```bash
java -jar target/middleware-resource-manager-0.0.1-SNAPSHOT-exec.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

### 3.7 部署前端

```bash
cd frontend
npm install
npm run build
# dist/ 目录部署到 Nginx
```

Nginx 配置参考现有 `release/nginx.conf`，确保 `/api` 代理到后端 8080 端口。

## 四、投产后操作

### 4.1 导入知识库文档

1. 登录系统 → 知识库管理
2. 批量上传技术文档（PDF/Word/Markdown）
3. 或从已有标准文档导入

### 4.2 验证搜索

在检索测试页面输入关键词，确认能搜到相关文档。

### 4.3 验证对话

在智能排查页面新建会话，提问测试。

## 五、监控与运维

### 5.1 关键指标

| 指标 | 监控方式 | 告警阈值 |
|------|---------|---------|
| GLM API 响应时间 | 应用日志 | > 30s |
| Milvus 连接状态 | /healthz | 非 healthy |
| 知识库文档数量 | 数据库查询 | 异常下降 |
| 对话失败率 | 应用日志 | > 5% |

### 5.2 常见问题

**Q: 搜索返回空结果？**
A: 检查 Milvus 是否运行，检查 embedding 模型是否可用。

**Q: 对话响应慢？**
A: 检查 GLM API 是否正常，检查网络连接。

**Q: 知识库文档丢失？**
A: 检查 Milvus 数据卷是否持久化，检查 MySQL 数据。

### 5.3 数据备份

```bash
# 备份 Milvus 数据
docker exec milvus tar czf /tmp/milvus_backup.tar.gz /var/lib/milvus
docker cp milvus:/tmp/milvus_backup.tar.gz /backup/

# 备份 MySQL 知识库表
mysqldump -u root middleware_resource_manager \
  knowledge_chunks chat_sessions chat_messages > /backup/knowledge_db.sql
```

## 六、版本变更记录

| 日期 | 版本 | 内容 |
|------|------|------|
| 2026-05-23 | v1.0 | 初始版本：知识库管理、智能排查、知识图谱 |
