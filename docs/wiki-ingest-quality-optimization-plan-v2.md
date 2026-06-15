# Wiki 编译质量优化方案 V2

**日期**: 2026-06-12
**基于**: `docs/wiki-ingest-quality-issues.md` 中的 8 个问题

---

## 问题优先级

| 优先级 | 问题 | 影响范围 | 复杂度 |
|:---:|------|---------|:---:|
| P0 | #5 Lint 结果过期显示 | 用户体验 | 低 |
| P0 | #6 图谱边数爆炸 | 用户体验 | 低 |
| P1 | #1 标题路径冗余 | 内容质量 | 中 |
| P1 | #2 标题软件名冗余 | 内容质量 | 低 |
| P1 | #4 过度压缩页面重编译 | 编译质量 | 中 |
| P2 | #3 增量编译逻辑 | 编译效率 | 高 |
| P2 | #8 任务管理页面 | 功能完整性 | 高 |
| P3 | #7 源文档向量化 | 搜索质量 | 高 |

---

## 方案 1：标题优化（#1 + #2）

### 问题根因

LLM 生成页面时，prompt 中没有明确约束标题格式，导致：
- 标题带上软件名前缀（如「TongWeb V7.0 集群管理 - XXX」）
- 内容小标题重复父级路径

### 解决方案

**修改 `IngestPromptTemplates` 中的页面生成 prompt**：

```
标题规范：
1. 页面标题不得包含软件名和版本号（这些信息由 category/software 标签承载）
2. 标题只描述本页面的核心内容，不重复父级路径
3. 内容中的小标题只写当前层级的标题，不写完整路径

示例：
- ✅ 正确：「License与节点管理」
- ❌ 错误：「TongWeb V7.0 集群管理 - License与节点管理」
```

**影响范围**：`IngestPromptTemplates.buildPlannedPageGenerationPrompt()`

**测试方法**：重新编译同一文档，对比新旧标题

---

## 方案 2：Lint 结果实时检测（#5）

### 问题根因

`loadLintResults()` 从数据库加载旧结果，不是实时计算。

### 解决方案

**方案 A（推荐）**：点击 Lint 标签时自动运行 lint

```javascript
// WikiPanel.vue
async function loadLintResults() {
  try {
    await runLint()  // 直接运行 lint，不加载旧结果
  } catch (e) {
    console.error('Failed to load lint results:', e)
  }
}
```

**方案 B**：显示"上次检测时间"，超过 5 分钟自动重新检测

```javascript
async function loadLintResults() {
  const lastRun = await request('/api/wiki/lint/last-run')
  if (Date.now() - new Date(lastRun).getTime() > 5 * 60 * 1000) {
    await runLint()
  } else {
    lintResults.value = await request('/api/wiki/lint/results')
  }
}
```

**推荐方案 A**：简单直接，用户体验好。

**影响范围**：`frontend/src/components/WikiPanel.vue`

---

## 方案 3：图谱边数过滤（#6）

### 问题根因

同一软件/同一来源的页面两两创建边，导致边数爆炸（48 个页面 → 2280 条边）。

### 解决方案

**方案 A（推荐）：前端过滤低权重边**

```javascript
// WikiPanel.vue - loadGraph()
async function loadGraph() {
  const raw = await request('/api/wiki/graph')
  const MIN_EDGE_WEIGHT = 3.0
  graphData.value = {
    ...raw,
    links: (raw.links || []).filter(l => (l.weight || 0) >= MIN_EDGE_WEIGHT)
  }
  initGraph()
}
```

**方案 B：后端过滤**

在 `WikiGraphService.buildGraph()` 中，生成边时过滤低权重：

```java
// 只保留 weight >= 3 的边
edges = edges.stream()
    .filter(e -> (double) e.get("weight") >= 3.0)
    .collect(Collectors.toList());
```

**方案 C：限制每个节点的最大边数**

```java
// 每个节点最多保留 10 条最高权重的边
Map<Integer, List<Map<String, Object>>> edgesByNode = new HashMap<>();
for (Map<String, Object> edge : edges) {
    edgesByNode.computeIfAbsent((int) edge.get("source"), k -> new ArrayList<>()).add(edge);
    edgesByNode.computeIfAbsent((int) edge.get("target"), k -> new ArrayList<>()).add(edge);
}
// 每个节点只保留 top 10
```

**推荐方案 A + B 组合**：后端过滤减少数据传输，前端过滤作为兜底。

**影响范围**：
- 方案 A：`frontend/src/components/WikiPanel.vue`
- 方案 B：`WikiGraphService.java`

---

## 方案 4：过度压缩页面重编译（#4）

### 问题根因

当前无法只重编译过度压缩的页面，只能全量重跑。

### 解决方案

**新增"重编译过度压缩页面"功能**：

```
1. 读取 quality_report.overCompressedPages 获取过度压缩页面列表
2. 读取 page_plan 获取这些页面的规划信息
3. 只针对这些页面重新生成内容（复用 section_facts）
4. 与现有页面合并（更新已有页面）
5. 重新跑质量门禁
```

**API 设计**：

```
POST /api/wiki/tasks/{taskId}/recompile-compressed
```

**实现要点**：
- 复用 `section_facts`（已持久化，250KB），跳过最耗时的步骤
- 只重生成 `overCompressedPages` 列表中的页面
- 使用 `page_plan` 中的规划信息指导生成
- 合并到现有页面（更新 content，不删除其他页面）

**影响范围**：
- `IngestAgent.java`：新增 `recompileCompressedPages()` 方法
- `IngestTaskService.java`：新增 `recompileCompressed()` 方法
- `WikiController.java`：新增 API 端点
- `WikiPanel.vue`：新增"重编译压缩页面"按钮

---

## 方案 5：增量编译逻辑（#3）

### 问题根因

当前是全量替换，PARTIAL 状态无法补充缺失章节。

### 解决方案

**新增"增量编译"模式**：

```
1. 读取上次 quality_report.missingSections 获取缺失章节列表
2. 读取 section_facts（已持久化）获取章节事实
3. 只针对缺失章节生成补充页面
4. 与现有页面合并（新增缺失页面，更新已有页面）
5. 重新跑质量门禁
```

**API 设计**：

```
POST /api/wiki/tasks/{taskId}/recompile-missing
```

**实现要点**：
- 复用 `section_facts`，跳过最耗时的步骤
- 只重生成 `missingSections` 对应的页面
- 使用 `page_plan` 中的规划信息指导生成
- 合并到现有页面（新增缺失页面，不删除已有页面）

**与方案 4 的关系**：
- 方案 4：重编译过度压缩页面（覆盖率 100%，但内容太短）
- 方案 5：重编译缺失章节（覆盖率 < 100%，有章节未覆盖）
- 两者可以共存，都是增量编译的特例

**影响范围**：
- `IngestAgent.java`：新增 `recompileMissingSections()` 方法
- `IngestTaskService.java`：新增 `recompileMissing()` 方法
- `WikiController.java`：新增 API 端点
- `WikiPanel.vue`：新增"重编译缺失章节"按钮

---

## 方案 6：来源详情页集成任务控制（#8）

### 设计思路

不新增任务管理页面，而是在现有的**来源详情页**（sources 标签）中增强编译状态展示和任务控制。

### 前端改动（WikiPanel.vue）

**来源详情页增加编译状态区域**：

```
┌─────────────────────────────────────────────┐
│ 来源详情                                     │
├─────────────────────────────────────────────┤
│ 标题: 100_TongWeb_V7.0集群管理指南.pdf       │
│ 分类: 中间件 / TongWeb                       │
│ 内容哈希: 09fbf8d7...                        │
├─────────────────────────────────────────────┤
│ 编译状态                                     │
│ ┌─────────────────────────────────────────┐ │
│ │ 状态: PARTIAL (88% 覆盖)                │ │
│ │ 进度: ████████████████████░░ 90%        │ │
│ │ 当前步骤: 正在执行质量门禁...             │ │
│ │ 创建时间: 2026-06-12 21:21              │ │
│ │ 耗时: 16 分 5 秒                        │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ [暂停] [继续] [重新编译] [重编译缺失章节]    │
│                                             │
│ 质量报告                                     │
│ ┌─────────────────────────────────────────┐ │
│ │ 覆盖率: 100% (397/397)                  │ │
│ │ 缺失章节: 0                             │ │
│ │ 过度压缩: 13 页                         │ │
│ │ 短页面: 0                               │ │
│ │ 泛化标题: 0                             │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ 编译历史                                     │
│ ┌─────────────────────────────────────────┐ │
│ │ #18 PARTIAL  21:21  16m  覆盖100%       │ │
│ │ #17 PARTIAL  20:46  19m  覆盖100%       │ │
│ │ #16 FAILED   20:31  10m  页面计划失败    │ │
│ └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

**按钮逻辑**：
- **暂停**：仅在 PROCESSING 状态显示，调用 `POST /api/wiki/tasks/{id}/pause`
- **继续**：仅在 PAUSED 状态显示，调用 `POST /api/wiki/tasks/{id}/resume`
- **重新编译**：在 COMPLETED/PARTIAL/FAILED 状态显示，调用现有编译接口
- **重编译缺失章节**：仅在 PARTIAL 状态且有 missingSections 时显示
- **重编译压缩页面**：仅在 PARTIAL 状态且有 overCompressedPages 时显示

### 后端改动

**新增 API**：
```
POST /api/wiki/tasks/{taskId}/pause              # 暂停任务
POST /api/wiki/tasks/{taskId}/resume             # 继续任务
POST /api/wiki/tasks/{taskId}/recompile-missing   # 重编译缺失章节
POST /api/wiki/tasks/{taskId}/recompile-compressed # 重编译压缩页面
```

**IngestTaskService 新增方法**：

```java
public void pauseTask(Long taskId) {
    // 更新任务状态为 PAUSED
    taskMapper.updateStatus(taskId, "PAUSED", null);
    // IngestAgent 在批次间检查此状态，自动暂停
}

public void resumeTask(Long taskId) {
    // 更新任务状态为 PROCESSING
    // 重新触发 executeTask
}
```

**IngestAgent 批次间检查**：

```java
// 在 section_facts 和 page_generation 的每个批次开始前
if (isTaskPaused(taskId)) {
    log.info("Task {} paused at batch {}/{}", taskId, batchIndex, totalBatches);
    return; // 保存当前进度，退出
}
```

### 暂停/继续实现方式

**状态标记 + 批次间检查（推荐）**：

- 优点：实现简单，不中断正在运行的 LLM 调用
- 缺点：暂停不是即时的，需要等待当前批次完成（通常 10-30 秒）
- 流程：
  1. 用户点击暂停 → 更新任务状态为 PAUSED
  2. 当前批次完成 → IngestAgent 检查状态 → 发现 PAUSED → 保存进度并退出
  3. 用户点击继续 → 更新任务状态为 PROCESSING → 重新触发 executeTask
  4. executeTask 从上次进度继续（复用 section_facts）

**影响范围**：
- `WikiPanel.vue`：来源详情页增加编译状态区域和按钮
- `WikiController.java`：新增 pause/resume/recompile API
- `IngestTaskService.java`：新增 pause/resume/recompile 方法
- `IngestAgent.java`：在批次间检查任务状态
- `IngestTaskMapper.java`：新增查询方法（按 source_id 查最新任务）

---

## 方案 7：源文档向量化（#7）

### 问题根因

当前只向量化 wiki 页面（LLM 生成的摘要），不向量化源文档原文。
Wiki 页面丢失了原文细节，搜索不够精准。

### 解决方案

**新增源文档向量化逻辑**：

```
1. 源文档上传后，按 chunk 切分（参考 knowledge 模块的 TextSplitter）
2. 每个 chunk 生成向量，存储到 Milvus
3. 元数据包含：source_id、source_title、chunk_index、category、software
4. Wiki 页面不再向量化
```

**存储设计**：

复用 `knowledge_chunks` 表，或新增 `wiki_source_chunks` 表：

```sql
CREATE TABLE wiki_source_chunks (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_id BIGINT NOT NULL,
  chunk_index INT NOT NULL,
  content TEXT NOT NULL,
  vector_id VARCHAR(100),
  category VARCHAR(50),
  software VARCHAR(100),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_source (source_id)
);
```

**搜索逻辑**：

```
用户搜索 → 向量检索 wiki_source_chunks → 返回源文档片段
```

**与现有搜索的整合**：
- 保留 wiki 页面的标题搜索（精确匹配）
- 向量搜索改为搜索源文档 chunk
- 搜索结果展示：源文档标题 + 匹配的 chunk 内容

**影响范围**：
- `IngestAgent.java`：修改 `vectorizePages()` 为 `vectorizeSource()`
- 新增 `SourceChunkService.java`：源文档 chunk 切分和向量化
- `WikiSearchService.java`：修改搜索逻辑
- `WikiPanel.vue`：修改搜索结果展示

---

## 实施顺序

```
Phase 1（快速修复，1-2 天）：
  #5 Lint 结果过期 → 修改 WikiPanel.vue
  #6 图谱边数过滤 → 修改 WikiGraphService.java + WikiPanel.vue

Phase 2（内容质量，2-3 天）：
  #1 + #2 标题优化 → 修改 IngestPromptTemplates
  #4 过度压缩重编译 → 新增 recompileCompressed API

Phase 3（增量编译，3-5 天）：
  #3 增量编译逻辑 → 新增 recompileMissing API

Phase 4（任务控制，3-5 天）：
  #8 来源详情页集成任务控制 → 增强 WikiPanel 来源详情 + 新增 pause/resume API

Phase 5（架构优化，7-10 天）：
  #7 源文档向量化 → 修改向量化逻辑 + 搜索逻辑

Phase 6（图谱优化，2-3 天）：
  #9 图谱按缩放比例加载节点 → 修改 WikiPanel.vue 图谱逻辑
```

---

## 方案 8：图谱按缩放比例加载节点（#9）

### 设计思路

图谱界面按放大比例逐步加载节点，避免一次性渲染所有节点导致性能问题和视觉混乱。

### 实现方式

**缩放级别与节点密度**：

```
缩放级别 0.5x（缩小）→ 只显示 OVERVIEW 页面 + 高权重边
缩放级别 1.0x（默认）→ 显示所有页面 + weight >= 3 的边
缩放级别 2.0x（放大）→ 显示所有页面 + weight >= 2 的边
缩放级别 3.0x+（放大）→ 显示所有页面 + 所有边
```

**前端实现**：

```javascript
// 监听缩放事件
graph.onZoom(({ k }) => {
  const zoomLevel = k
  // 根据缩放级别过滤节点和边
  const filtered = filterByZoom(graphData.value, zoomLevel)
  graph.graphData(filtered)
})

function filterByZoom(data, zoom) {
  if (zoom < 0.8) {
    // 缩小：只显示 OVERVIEW 页面
    return {
      nodes: data.nodes.filter(n => n.pageType === 'OVERVIEW'),
      links: data.links.filter(l => l.weight >= 5)
    }
  } else if (zoom < 1.5) {
    // 默认：显示所有页面，高权重边
    return {
      nodes: data.nodes,
      links: data.links.filter(l => l.weight >= 3)
    }
  } else {
    // 放大：显示所有页面和边
    return data
  }
}
```

**性能优化**：
- 使用 `requestAnimationFrame` 避免频繁更新
- 缩放级别变化时添加防抖（debounce 200ms）
- 节点数量超过阈值时降低渲染质量

**影响范围**：
- `WikiPanel.vue`：修改图谱初始化逻辑，添加缩放监听

---

## 风险评估

| 方案 | 风险 | 缓解措施 |
|------|------|---------|
| 标题优化 | LLM 可能不完全遵循 prompt | 添加后处理：自动去除软件名前缀 |
| Lint 实时检测 | 每次点击都运行 lint，性能开销 | 缓存结果 5 分钟 |
| 图谱边过滤 | 可能过滤掉有意义的弱关联 | 设置合理的阈值（weight >= 3） |
| 过度压缩重编译 | 重编译后可能仍然过度压缩 | 增加重试次数限制 |
| 增量编译 | 新旧页面风格不一致 | 统一 prompt 模板 |
| 来源详情任务控制 | 暂停不是即时的（需等当前批次） | 用户体验可接受，批次通常 10-30 秒 |
| 源文档向量化 | 需要大量改动 | 分阶段实施，先支持新文档 |

---

## 修复审查结论与执行要求（2026-06-13）

### 审查结论

当前 V2 方案方向基本正确，但已有实现仍停留在“入口接线 + 部分 happy path”阶段，不能直接合入或上线。必须先修复构建、质量门禁、暂停/继续和图谱限边这些闭环问题，再继续推进源文档向量化等架构优化。

已确认的关键问题：

1. `WikiController` 新增重编译接口的 `recordAudit(...)` 调用参数不匹配，`mvn clean compile -q` 会失败。
2. 重编译缺失章节和过度压缩页面后，没有重新执行质量门禁，而是直接标记 `COMPLETED`，会造成任务状态和质量报告失真。
3. `SKIPPED` 结果会继续走成功路径，可能显示“重编译完成”，但实际上没有生成或修复任何页面。
4. 暂停功能只把任务状态写成 `PAUSED`，主编译流程没有在批次间检查暂停状态；同时 `updateProgress` 会把状态重新覆盖为 `PROCESSING`。
5. 继续任务并不是从暂停点继续，当前实现会重新触发主编译流程，和方案描述不一致。
6. 图谱边数过滤只做了前端兜底，后端仍然计算和传输全量边，不能从根上解决边数爆炸。
7. 标题优化只依赖 prompt，不够稳定，需要保存前后处理兜底，避免软件名、版本号和父级路径重复。

### Phase 0：修复构建阻断

必须先修复 `WikiController` 中新增接口的审计日志调用。

要求：

- `recompile-compressed`、`recompile-missing`、`pause`、`resume` 统一使用现有审计方法签名：
  `recordAudit(action, targetType, targetId, authentication, request, detail)`。
- 不新增拼接式审计 JSON 工具，保持当前控制器风格即可。

验收：

- `mvn clean compile -q` 通过。
- `mvn test -Dapp.vector.type=memory` 通过。

### Phase 1：修复重编译质量闭环

重编译不是“生成几页就完成”，必须重新计算完整质量报告。

正确流程：

```text
读取 task.quality_report
读取 task.section_facts
读取 task.page_plan
筛选目标 plans
生成目标 pages
保存或更新 pages
重新解析 links
重新拉取当前 source 关联页面
重新执行 WikiIngestQualityGate.evaluate(outline, pages)
更新 task.quality_report
根据质量报告设置 COMPLETED / PARTIAL / FAILED
再决定 source.ingested 状态
```

实现要求：

- `IngestAgent.recompileCompressedPages(...)` 和 `IngestAgent.recompileMissingSections(...)` 保存页面后必须重新运行 `WikiIngestQualityGate`。
- `IngestResult.status` 必须来自最新质量报告，不能固定写 `SUCCESS`。
- `IngestResult.qualityReport` 必须写入最新质量报告 JSON。
- `IngestTaskService.recompileCompressed(...)` 和 `recompileMissing(...)` 必须调用 `persistQualityReport(taskId, result)`。
- 如果结果仍是 `PARTIAL`，任务保持 `PARTIAL`，错误信息写质量摘要。
- 如果结果是 `SKIPPED`，不要调用 `updateResult()`，不要把任务标记为完成，应保留原状态或返回明确提示。

验收：

- 缺失章节重编译后，`quality_report.missingSections` 会重新计算，不会被伪造成空列表。
- 过度压缩页面重编译后，`quality_report.overCompressedPages` 会重新计算。
- 重编译失败、跳过、部分成功、完全成功四种状态前端显示一致。

### Phase 2：修复暂停/继续真实语义

暂停建议先实现为“批次间暂停”，不要强制中断正在执行的 LLM 调用。

实现要求：

- 将 `taskId` 或 `PauseChecker` 传入 `IngestAgent.ingestPlanned(...)`。
- 在以下阶段检查暂停：
  - section_facts 每个 batch 开始前。
  - page_plan 开始前和完成后。
  - page_generation 每个 batch 开始前。
  - quality_gate 开始前。
- 检测到暂停时返回 `IngestResult.status = "PAUSED"` 或抛内部 `TaskPausedException`，由 `IngestTaskService` 转为任务状态 `PAUSED`。
- 暂停时不能标记 source 已编译，不能覆盖已有质量报告，必须保留已持久化的中间产物。
- `updateProgress` / `updateProgressWithTotal` 不应无条件写 `status='PROCESSING'`。可以改为只更新进度字段，或增加 `WHERE status != 'PAUSED'` 条件。

继续策略：

- 如果已有 `section_facts` 和 `page_plan`，优先从页面生成或质量修复阶段继续。
- 如果只有 `section_facts`，从 page_plan 阶段继续。
- 如果没有可复用产物，才从头开始。
- 在没有持久化 batch index 前，不要在文案里承诺“精确从暂停点继续”。

验收：

- 点击暂停后，当前 LLM batch 完成后任务稳定停在 `PAUSED`。
- 前端刷新后仍显示 `PAUSED`，不会被进度更新覆盖回 `PROCESSING`。
- 点击继续后不会无脑重新抽取全部 section_facts。

### Phase 3：标题质量兜底

Prompt 约束需要保留，但不能只依赖 LLM 遵守。

实现要求：

- 保存页面前增加标题和正文小标题 normalize：
  - 页面标题去掉软件名和版本号前缀。
  - 正文 `##`、`###` 小标题去掉重复父级路径。
  - 保留必要区分词，避免多个页面被规范化成同名标题。
- `enrichPagesWithPlan(...)` 不要只依赖标题匹配，标题 normalize 后必须仍能匹配到对应 plan。优先使用 batch 内顺序、计划 ID 或 `planned_title` 映射。

验收：

- 新编译页面标题不再批量带软件名和版本号。
- 正文小标题不再重复完整章节路径。
- `duplicateTitles` 不因为标题 normalize 明显升高。

### Phase 4：后端图谱限边

前端过滤只能缓解显示，后端必须减少输出边数。

实现要求：

- `WikiGraphService` 输出 links 前过滤：
  - 默认只返回 `weight >= 3` 的边。
  - 每个节点最多保留 Top N 条边，建议 N=10 或 N=15。
  - 直接链接边永远保留。
  - 同软件、同来源这类弱关联可用于社区聚类，但不必全部输出到前端。
- 前端保留当前 `weight >= 3` 过滤作为兜底。
- 社区聚类仍按软件类型划分，不要拆得过细。

验收：

- 48 个 wiki 页面不再返回 2000+ 条边。
- 图谱显示节点数量和数据库页面数量一致，不再视觉上像 1000+ 节点。
- 软件类型社区聚类稳定。

### Phase 5：Lint 过期结果修复补充

当前“点击 Lint 自动运行”方向可以保留，但要避免并发触发。

实现要求：

- `loadLintResults()` 中如果 `lintRunning === true`，直接返回。
- 自动运行失败时通过 `notify` 提示，不只写 `console.error`。
- 可选增加 `lastRunAt` 展示，但不是本阶段必须项。

验收：

- 切换到 Lint 标签不会展示旧结果。
- 快速连续点击不会并发启动多个 lint。

### Phase 6：源文档向量化后置

源文档向量化属于架构优化，不应和本轮质量闭环修复混在一起。

后续单独设计：

- 新增 `wiki_source_chunks` 或复用明确隔离的 chunk 表。
- 按文档 section/chunk 对源文档原文向量化。
- Wiki 页面只作为结构化阅读和导航入口，不再承担原文细节检索职责。
- 搜索优先返回源文档片段，再关联到 Wiki 页面。

### 必补测试

- `mvn clean compile -q` 作为基础验收。
- `recompileMissingSections`：
  - 有 `missingSections` 时只生成相关 plans。
  - 重编译后重新写 `quality_report`。
  - 仍缺失时状态保持 `PARTIAL`。
- `recompileCompressedPages`：
  - 只重生成 `overCompressedPages` 对应页面。
  - 重编译后重新计算 `overCompressedPages`。
- 暂停/继续：
  - `PAUSED` 不被 `updateProgress` 覆盖。
  - 批次间检测到暂停后退出。
  - 继续优先复用已有中间产物。
- 图谱：
  - 同软件 48 页面时后端输出边数受控。
  - 直接链接不被过滤。

### 推荐执行顺序

1. 修复 P0 编译失败。
2. 修复重编译质量门禁闭环。
3. 修复 `SKIPPED` 被当成功的问题。
4. 修复暂停/继续真实语义。
5. 增加标题 normalize 兜底。
6. 增加后端图谱限边。
7. 补测试和 DB 记录。
8. 用同一个 TongWeb PDF 验证全量编译、缺失章节重编译、压缩页面重编译、暂停/继续。
