# 知识库文档管理优化

## Context

需要清理测试文档、在知识库页面增加文档列表和删除功能、修复侧边栏滚动问题。

## 任务拆分（3 个 Agent 并行）

### Agent 1: 后端 — 删除测试文档 + 删除 API

1. **删除测试文档**：直接执行 SQL `DELETE FROM knowledge_chunks WHERE source_title LIKE 'test%' OR source_title LIKE 'proxy-test%'`

2. **新增删除 API**：`DELETE /api/knowledge/docs?title=xxx&sourceType=UPLOAD`
   - KnowledgeChunkRepository 新增 `deleteBySourceTitleAndSourceType(String, String)`
   - KnowledgeService 新增 `deleteDocument(String title, String sourceType)` — 同时删除 DB 和向量库
   - InMemoryVectorStore 的 `delete(id)` 已有，需要通过 vectorId 关联删除

3. **修改 findDistinctSources 返回值**：加上 `vector_id` 列，方便删除时清理向量

### Agent 2: 前端 — 知识库页面增加文档列表 + 删除功能

1. **KnowledgePanel.vue** 新增"已导入文档"区域：
   - 调用 `GET /api/knowledge/docs` 获取列表
   - 每个文档显示：图标、标题、切片数、删除按钮
   - 删除时调用 `DELETE /api/knowledge/docs?title=xxx&sourceType=xxx`
   - 删除后刷新列表

2. **DiagnosticsPanel.vue** 左侧边栏高度修复：
   - `.session-sidebar` 加 `overflow: hidden`
   - `.kb-list` 区域独立滚动，不影响页面高度
   - 整个 sidebar 固定高度，内部 session-list 和 kb-section 各自滚动

### Agent 3: 文档更新

- 更新 README.md 的 API 列表
- 同步 plan 到 docs/

## 验证

1. 测试文档已删除
2. 知识库页面显示文档列表，可删除
3. 排查页面左侧边栏固定高度，列表独立滚动
