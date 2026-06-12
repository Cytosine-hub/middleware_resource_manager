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

### 优化 1：section_facts batch 并发执行 [高收益]

**改动文件**：`IngestAgent.java`

**方案**：将 `generateSectionFacts()` 中的串行 for 循环改为 `CompletableFuture` 并发执行。

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
- 并发数不超过 `LLM_MAX_CONCURRENT`（默认 3），避免超出 PooledChatModel 信号量限制
- batch 结果按 section order 合并，保证 section_facts 顺序一致
- 单批失败不影响其他批（已有 fallback 机制）
- 不需要新线程池，复用 Spring 的 `@Async` 或 `ForkJoinPool.commonPool()`

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

**方案**：当前 `toCompactOutlineJson()` 为每个 section 输出了 10+ 个字段（id、path、level、order、pageRange、sourceSignal、required、sectionType、confidence、excerpt、blocks）。section_facts 只需要：`id`、`path`、`excerpt`、`sectionType`、`blocks`。

新增一个 `toFactsOnlyOutlineJson()` 方法，只发送 section_facts 需要的字段。

**预期收益**：减少 prompt_tokens ~20%，LLM 处理更快。预估减少 5-10 秒/batch。

### 优化 4：扩展本地事实跳过范围 [中收益]

**改动文件**：`IngestAgent.java`

**方案**：扩展 `canUseLocalSectionFact()` 判定条件：

1. excerpt 纯数字/纯编号（如 "1.1"、"- 1"）
2. excerpt 全是重复标题文字 + 少量过渡句（如 "本章节描述了..."）
3. excerpt 长度 < 50 字且不含参数、命令、指标等关键词

新增一个 `isLowInformationSection()` 方法，和 `canUseLocalSectionFact()` 联合判定。

**预期收益**：对长文档（50+ section）可能跳过 30-50% 的 LLM 调用。对 19 section 短文档效果有限。

### 优化 5：page_generation batch 并发执行 [中收益，不在 section_facts 内]

**说明**：page_generation 阶段也是串行执行 batch。和优化 1 同样的方案，对 `generatePagesFromPlan()` 中的 batch 循环改为并发。

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
