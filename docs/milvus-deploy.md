# Milvus 向量数据库部署手册

## 一、概述

Milvus 是生产环境的向量数据库，用于存储知识库文档的 embedding 向量，支持高效的相似度检索。

本项目开发环境使用 `InMemoryVectorStore`，生产环境必须切换为 Milvus。

## 二、环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Docker | 20.10+ | 容器化部署 |
| CPU | 4 核+ | 推荐 8 核 |
| 内存 | 8 GB+ | 推荐 16 GB |
| 磁盘 | 50 GB+ SSD | 向量数据存储 |

## 三、部署方式

### 3.1 Docker 单机部署（推荐中小规模）

```bash
# 拉取镜像
docker pull milvusdb/milvus:v2.4-latest

# 创建数据目录
mkdir -p /data/milvus

# 启动 Milvus Standalone
docker run -d \
  --name milvus \
  -p 19530:19530 \
  -p 9091:9091 \
  -v /data/milvus:/var/lib/milvus \
  -e ETCD_USE_EMBED=true \
  -e COMMON_STORAGETYPE=local \
  --restart always \
  milvusdb/milvus:v2.4-latest \
  milvus run standalone
```

### 3.2 Docker Compose 部署

创建 `docker-compose.yml`：

```yaml
version: '3.5'

services:
  milvus:
    image: milvusdb/milvus:v2.4-latest
    container_name: milvus
    command: milvus run standalone
    ports:
      - "19530:19530"
      - "9091:9091"
    volumes:
      - /data/milvus:/var/lib/milvus
    environment:
      - ETCD_USE_EMBED=true
      - COMMON_STORAGETYPE=local
    restart: always
    deploy:
      resources:
        limits:
          memory: 8G
          cpus: '4'
```

启动：

```bash
docker-compose up -d
```

### 3.3 国内镜像拉取

Docker Hub 网络不通时，可配置镜像加速器或使用代理：

```bash
# 方式一：配置 Docker daemon 镜像加速（编辑 /etc/docker/daemon.json）
{
  "registry-mirrors": [
    "https://<your-id>.mirror.aliyuncs.com"
  ]
}
# 注：阿里云加速器地址需在阿里云容器镜像服务控制台获取

# 方式二：通过代理拉取
export HTTP_PROXY=http://proxy-server:port
export HTTPS_PROXY=http://proxy-server:port
docker pull milvusdb/milvus:v2.4-latest

# 方式三：离线导入（在有网络的机器上导出，传输到目标机器）
# 导出
docker save milvusdb/milvus:v2.4-latest -o milvus-v2.4.tar
# 传输到目标机器后导入
docker load -i milvus-v2.4.tar
```

## 四、验证部署

```bash
# 检查容器状态
docker ps | grep milvus

# 健康检查
curl http://localhost:9091/healthz
# 返回 {"status":"healthy"} 即成功

# 检查端口
ss -tlnp | grep 19530
```

## 五、应用配置

### 5.1 环境变量

```bash
# Milvus 连接地址
export MILVUS_HOST=milvus-server-ip
export MILVUS_PORT=19530
```

### 5.2 application-prod.yml（已有）

```yaml
app:
  vector:
    type: milvus
    host: ${MILVUS_HOST:localhost}
    port: ${MILVUS_PORT:19530}
    collection: knowledge_chunks
```

向量维度由 `MilvusVectorStore.java` 中的 `VECTOR_DIM = 1024` 决定，与 bge-large 模型匹配。生产环境如使用 GLM embedding-3（维度 1024），无需修改。

### 5.3 应用启动

```bash
export GLM_API_KEY=your-glm-api-key
export MILVUS_HOST=10.x.x.x
export MILVUS_PORT=19530

java -jar middleware-resource-manager.jar \
  --spring.profiles.active=prod \
  --server.port=8080
```

应用启动时会自动创建 `knowledge_chunks` collection 和 IVF_FLAT 索引。

## 六、数据备份与恢复

### 6.1 备份

```bash
# 停止 Milvus（确保数据一致性）
docker stop milvus

# 备份数据目录
tar czf /backup/milvus_$(date +%Y%m%d).tar.gz /data/milvus

# 启动 Milvus
docker start milvus
```

### 6.2 恢复

```bash
docker stop milvus
tar xzf /backup/milvus_20260523.tar.gz -C /
docker start milvus
```

## 七、运维监控

### 7.1 健康检查

```bash
# HTTP 健康接口
curl http://localhost:9091/healthz

# 容器日志
docker logs -f milvus --tail 100
```

### 7.2 关键指标

| 指标 | 告警阈值 | 处理方式 |
|------|---------|---------|
| 健康检查失败 | 连续 3 次 | 重启容器 |
| 磁盘使用率 | > 80% | 清理或扩容 |
| 内存使用率 | > 90% | 扩容或优化索引 |
| 检索延迟 | > 500ms | 检查索引类型和数据量 |

### 7.3 常用运维命令

```bash
# 查看 collection 信息（需要安装 milvus-cli 或使用 Attu）
docker exec -it milvus milvus-cli

# 查看容器资源占用
docker stats milvus

# 重启
docker restart milvus

# 查看版本
docker exec milvus milvus --version
```

## 八、Attu 可视化管理（可选）

Attu 是 Milvus 的官方 GUI 管理工具：

```bash
docker run -d \
  --name attu \
  -p 8000:3000 \
  -e MILVUS_URL=milvus-server-ip:19530 \
  zilliz/attu:latest
```

访问 `http://server-ip:8000` 即可管理 collections、查看数据、执行查询。

## 九、升级与迁移

### 9.1 版本升级

```bash
# 备份数据
docker stop milvus
tar czf /backup/milvus_backup.tar.gz /data/milvus

# 拉取新版本
docker pull milvusdb/milvus:v2.5-latest

# 用新版本启动（数据目录不变）
docker rm milvus
docker run -d --name milvus \
  -p 19530:19530 -p 9091:9091 \
  -v /data/milvus:/var/lib/milvus \
  --restart always \
  milvusdb/milvus:v2.5-latest \
  milvus run standalone
```

### 9.2 从 InMemoryVectorStore 迁移到 Milvus

切换 `app.vector.type` 为 `milvus` 后，需要重新导入知识库文档：

1. 启动应用，连接 Milvus
2. 进入知识库管理页面
3. 批量上传所有文档
4. 或从标准文档重新导入

向量数据不会自动迁移，需要通过重新导入生成。
