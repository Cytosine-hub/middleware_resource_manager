# Wiki 编译 section_facts 阶段性能优化方案

## 现状

2026-06-12 端到端测试数据（364K PDF，19 个 section，2 个 batch）：

| 指标 | 值 |
|------|------|
| section_facts 总耗时 | 199,999ms（3 分 20 秒），占编译总耗时 44.8% |
| batch 数 | 2（12 + 7 sections） |
| 单 batch LLM 耗时 | ~100 秒 |
| LLM 调用次数 | 5（section_facts 2 次 + page_plan 1 次 + page_gen 2 次） |
| 总 token 消耗 | 15,941 输入 + 24,247 输出 |

编译总耗时 7 分 26 秒，section_facts 是最大瓶颈。

## 瓶颈分析

### 1. 串行执行

当前 `generateSectionFacts()` 内部 for 循环逐批调用 LLM，batch 1 完成后才调 batch 2：

```text
batch 1 (12 sections) → LLM (~100s) → batch 2 (7 sections) → LLM (~100s)
总计: ~200s
```

如果改为并发，理论上可以降到 ~100s（最慢 batch 的耗时）。

### 2. LLM 响应慢

外部 LLM API（glm-4-plus / mimo-v2.5-pro）单次调用 ~100 秒。影响因素：
- `AI_MAX_TOKENS=8192`，section_facts 实际输出通常 2000-4000 token，但模型仍按 8192 分配输出预算
- Prompt 中 excerpt 限制 500 字符/section，但 12 个 section 的 outline JSON 仍然较长

### 3. 低价值 section 未充分过滤

`canUseLocalSectionFact()` 只跳过"标题和摘录完全相同"的 section。有些 section 有少量正文但信息量极低（如过渡段落、图注、页眉重复内容），仍然被送入 LLM。

## 优化方案

## 2026-06-12 实施进度

已完成：

- `section_facts` batch 并发执行：改为受控共享线程池，完成一个 batch 即合并结果并上报进度。
- `page_generation` batch 并发执行：按完成顺序消费结果，最终按 batch index 合并，保证输出顺序稳定。
- batch 级容错：单个 section_facts/page_generation batch 的 LLM 异常或 JSON 解析失败会使用 deterministic fallback，不再导致整个阶段失败。
- JSON 解析增强：支持从前后混入说明文字的响应中提取平衡 JSON 对象；解析失败日志输出 head/tail，便于判断截断或尾部污染。
- 进度修复：批次完成后立即更新 `completed_chunks`，不再等全部 future 完成后一次性刷新。
- 任务列表质量报告修复：列表查询保留 `quality_report`，只排除较大的 `section_facts` 和 `page_plan`。
- 完成态进度修复：任务结束前重新读取当前 `total_chunks`，避免用创建任务时的旧 chunk 数覆盖批次进度。
- facts-only compact outline：`section_facts` 阶段只发送事实抽取所需字段，减少 prompt 输入体积。
- 低信息章节本地跳过：短且不含操作、配置、故障、监控等信号的章节使用 deterministic facts，不再调用 LLM。

待完成：

- section_facts 阶段独立 `max_tokens` 配置。
- 基于真实长文档样例继续校准低信息章节跳过阈值和关键词。

### 优化 1：section_facts batch 并发执行 [高收益]

**改动文件**：`IngestAgent.java`

**状态**：已完成。当前实现使用 `ExecutorCompletionService` 和类级共享 `llmBatchExecutor`，并发数来自 `app.llm.max-concurrent`。

```java
// 伪代码
ExecutorService executor = Executors.newFixedThreadPool(Math.min(batches.size(), maxConcurrent));
List<CompletableFuture<JsonArray>> futures = batches.stream()
    .map(batch -> CompletableFuture.supplyAsync(() -> processSectionFactsBatch(batch, outline, progress, llmMetrics), executor))
    .toList();
// 等待所有 batch 完成，按 section order 合并
List<JsonArray> results = futures.stream().map(CompletableFuture::join).toList();
```

**约束**：
- 并发数不超过 `app.llm.max-concurrent`，避免超出 PooledChatModel 信号量限制
- batch 结果按 section order 合并，保证 section_facts 顺序一致
- 单批失败不影响其他批，失败批次使用 fallback section facts
- 使用类级共享线程池，避免热路径重复创建线程池

**预期收益**：2 batch 场景从 ~200s 降到 ~100s（减少 50%）。大文档（10+ batch）收益更大。

**风险**：
- 并发 LLM 调用可能触发供应商限流 → 已有 `PooledChatModel` 信号量保护
- 需要线程安全的 `LlmMetrics` → 已实现 synchronized 方法

### 优化 2：降低 section_facts 输出 token 预算 [中收益]

**改动文件**：`IngestAgent.java`、`IngestPromptTemplates.java` 或 `application.yml`

**方案**：为 section_facts 阶段使用独立的、较低的 `max_tokens`。

当前 `callLlm()` 使用全局 `AI_MAX_TOKENS=8192`。section_facts 的 12 个 section 输出通常 < 4000 token。

选项 A（推荐）：在 `callLlm()` 中增加可选 `maxTokens` 参数，section_facts 调用时传入 4096。

选项 B：在 prompt 中明确限制输出条目数和长度。

**预期收益**：减少模型生成长度预算，LLM 更快完成推理。预估减少 10-20 秒/batch。

### 优化 3：精简 section_facts prompt 输入 [低收益]

**改动文件**：`IngestAgent.java`

**状态**：已完成。

**方案**：当前 `toCompactOutlineJson()` 为每个 section 输出了 10+ 个字段（id、path、level、order、pageRange、sourceSignal、required、sectionType、confidence、excerpt、blocks）。section_facts 只需要：`id`、`path`、`excerpt`、`sectionType`、`blocks`。

已新增 `toFactsOnlyOutlineJson()` 方法，只发送 section_facts 需要的字段，并将 excerpt 限制在较短长度内。

**预期收益**：减少 prompt_tokens ~20%，LLM 处理更快。预估减少 5-10 秒/batch。

### 优化 4：扩展本地事实跳过范围 [中收益]

**改动文件**：`IngestAgent.java`

**状态**：已完成第一版，后续根据真实长文档样例继续调阈值。

**方案**：扩展 `canUseLocalSectionFact()` 判定条件：

1. excerpt 纯数字/纯编号（如 "1.1"、"- 1"）
2. excerpt 全是重复标题文字 + 少量过渡句（如 "本章节描述了..."）
3. excerpt 长度 < 50 字且不含参数、命令、指标等关键词

已新增 `isLowInformationSection()` 方法，和 `canUseLocalSectionFact()` 联合判定；含操作、配置、命令、端口、故障、日志、监控等关键词的短章节仍会进入 LLM。

**预期收益**：对长文档（50+ section）可能跳过 30-50% 的 LLM 调用。对 19 section 短文档效果有限。

### 优化 5：page_generation batch 并发执行 [中收益，不在 section_facts 内]

**状态**：已完成。

**说明**：page_generation 阶段使用与 section_facts 相同的共享线程池和完成即消费模式；单批 LLM 调用失败或返回非法 JSON 时，仅该批次使用 fallback 页面。

**预期收益**：364K PDF 的 page_gen 从 ~146s 降到 ~77s（2 batch）。

## 优先级

| 优先级 | 优化项 | 预期收益 | 实现复杂度 | 风险 |
|--------|--------|---------|-----------|------|
| P0 | 1. batch 并发 | 50% 时间 | 中 | 低（已有信号量保护） |
| P1 | 2. 降低 max_tokens | 10-20s/batch | 低 | 无 |
| P1 | 5. page_gen 并发 | 50% page_gen 时间 | 中 | 低 |
| P2 | 3. 精简 prompt 输入 | 5-10s/batch | 低 | 无 |
| P2 | 4. 扩展本地跳过 | 30-50% LLM 调用（长文档） | 中 | 低 |

## 预期总体效果

对 364K PDF（19 section，2 batch）：

| 阶段 | 优化前 | 优化后（预估） |
|------|--------|--------------|
| section_facts | 200s | ~100s（并发） |
| page_plan | 100s | 100s（无法并发，单次调用） |
| page_gen | 146s | ~77s（并发） |
| 其他 | ~0s | ~0s |
| **总计** | **~446s** | **~277s** |

总编译时间减少约 **38%**。

对长文档（5.9M PDF，468 section，~40 batch）效果更显著：
- section_facts 从 ~70 分钟降到 ~18 分钟（假设并发 3 batch，总 batch 数 40/3 ≈ 14 轮 × 100s）
- page_gen 同理受益

## 实施建议

1. 先实现优化 1（batch 并发），这是收益最大的一项
2. 同时实现优化 2（降低 max_tokens），简单且无风险
3. 编译测试验证 timing 数据
4. 如效果仍不理想，再实现优化 3-5
