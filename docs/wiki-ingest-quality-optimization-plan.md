# Wiki 文档编译质量优化方案

## 结论

当前 Wiki 编译质量问题的核心是现有编译链路缺少“文档类型识别 + 目录/章节级覆盖控制”。长文档被拆成多个 chunk 后独立交给 LLM，每个 chunk 只会生成它认为显眼的实体或概念页面，系统没有先判断文档是什么类型、应该按什么结构拆 section、整篇文档应该产出哪些页面，也没有在保存前检查章节覆盖率。

优化主线应改为通用的“目录驱动编译流程”：

1. 先抽取全文目录和章节结构。
2. 再生成 `page_plan`，明确每个计划页面覆盖哪些章节。
3. 按 `page_plan` 生成页面，而不是让每个 chunk 自由生成。
4. 保存页面时写入章节级 `source_refs`。
5. 通过质量门禁决定任务是否成功、部分成功或失败。

不同文档类型只影响 section 拆分、required section 判定和 page_plan 规划偏好，不应复制多套编译流程。安装指南、配置手册、监控手册、排障手册、产品说明、标准规范都走同一条主链路。

向量存储按中间件、数据库等分类拆 collection 可能减少跨领域召回噪声，但不是当前编译质量的主矛盾。更推荐先做“单 collection + category/software/source_type 标量过滤”，必要时再升级为 Milvus partition 或多 collection。

## 现状评估

### 已具备的基础能力

- 文档上传可携带分类和软件信息，并创建异步 Ingest 任务。
- 长文档已有分段处理，任务可在部分分段失败时进入 `PARTIAL`。
- 页面输出已有基础 JSON 校验，包括页面数组、标题、正文、页面类型和摘要长度。
- `wiki_pages` 已有 `category`、`software`、`version`、`source_refs` 等字段基础。
- 图谱已有 `wiki_links`、同软件、同分类、同源等关联信号雏形。
- 知识库向量导入 metadata 已包含 `category`、`software`、`sourceType`、`sourceId` 等信息。

### 主要缺口

- 长文档仍然是“分段独立编译”，不是“整篇规划后编译”。
- 切块策略对 PDF/Word/Markdown 的结构差异处理不足，不能可靠识别 `1.1`、`第 1 章`、`一、环境准备`、Word 标题样式、PDF 目录页、Markdown 标题层级等结构锚点。
- Prompt 目标仍偏向“实体/概念生成页面”，不是“覆盖文章目录”。
- 没有 `page_plan` 阶段，页面生成后直接落库。
- 校验只检查 JSON 结构，不检查章节覆盖率、来源引用、短页面和泛化标题。
- 长文档分段路径新建页面时 `source_refs` 不完整，缺少章节路径、页码、section id 和证据。
- 合并策略按 `title + page_type` 粗匹配，容易让后续分段覆盖前面细节。
- 任务成功条件过低，只要生成或更新了页面，就可能把 source 标记为已编译。
- 向量检索接口没有结构化过滤，容易跨分类召回不相关 chunk。

## 目标

1. 长文档编译从“抓显眼概念”升级为“按目录完整编译”。
2. 每个 Wiki 页面都能追溯到原文 source、章节、页码或段落范围。
3. 编译任务必须暴露覆盖率、缺失章节和质量问题。
4. 低覆盖率结果不能被静默标记为成功。
5. 安装指南、配置手册、监控手册、排障手册、产品说明等文档类型作为规划策略，而不是独立流程。
6. 检索阶段支持按分类、软件、来源类型过滤，减少向量召回噪声。
7. 图谱社区命名稳定、唯一、可解释。

## 设计原则

- 目录优先：先理解文章结构，再决定页面结构。
- 规划先行：所有长文档必须先产出 `page_plan`，再生成页面。
- 证据绑定：页面内容必须能回溯到 source 和 section。
- 质量可阻断：覆盖率不足时任务不能自动成功。
- 通用主链路：不同文档类型只切换解析和规划策略，不复制编译链路。
- 结构化检索优先：category/software/version/source_type 应作为检索过滤条件，不依赖物理拆库兜底。

## 目标架构

```text
上传文档
  |
  v
内容抽取
  |
  v
DocumentTypeClassifier
  - 识别文档类型
  - 选择拆分策略
  |
  v
DocumentOutlineExtractor
  - 标题
  - 章节层级
  - section_id
  - section_path
  - 页码/段落范围
  - required 标记
  |
  v
Section Fact Extraction
  - 每个章节提取事实
  - 不直接生成页面
  |
  v
Page Planner
  - 根据全文目录和 section_facts 生成 page_plan
  - 明确每个页面覆盖哪些章节
  |
  v
Page Generator
  - 按 page_plan 生成页面
  - 写入 source_refs 和 coverage
  |
  v
Merge & Link Resolver
  - 章节级合并
  - 解析 wikilink
  |
  v
Quality Gate
  - 覆盖率
  - source_refs 完整性
  - 短页面
  - 重复标题
  - 泛化标题
  |
  v
DRAFT / PARTIAL / FAILED
```

## P0：目录驱动编译主链路

### 1. 新增 DocumentTypeClassifier

`DocumentTypeClassifier` 在目录抽取前运行，负责判断文档的业务类型和结构特征：

```json
{
  "document_type": "CONFIG_GUIDE",
  "confidence": 0.86,
  "format": "PDF",
  "structure_quality": "MEDIUM",
  "signals": [
    "存在目录页",
    "包含大量配置项表格",
    "章节标题使用 1.1/1.2 编号"
  ]
}
```

建议先支持这些文档类型：

| document_type | 典型内容 | section 拆分重点 | page_plan 偏好 |
| --- | --- | --- | --- |
| `PRODUCT_OVERVIEW` | 产品介绍、版本说明、能力说明 | 产品、版本、模块、特性 | ENTITY、CONCEPT |
| `INSTALL_GUIDE` | 环境准备、部署、启动、验证 | 前置条件、步骤、命令、验证 | RUNBOOK、STANDARD |
| `CONFIG_GUIDE` | 配置文件、参数、默认值、示例 | 配置项、表格、代码块、注意事项 | STANDARD、RUNBOOK |
| `MONITORING_GUIDE` | 指标、端点、接入方式 | 指标分组、采集方式、权限 | RUNBOOK、CONCEPT |
| `TROUBLESHOOTING` | 故障现象、日志、根因、修复 | 症状、检查、原因、处理、验证 | EXPERIENCE、RUNBOOK |
| `STANDARD_SPEC` | 规范、基线、检查项 | 条款、等级、适用范围 | STANDARD |
| `RELEASE_NOTE` | 版本变化、缺陷修复、兼容性 | 版本、变更项、影响范围 | CONCEPT、EXPERIENCE |

分类结果只决定拆分和规划策略，不决定是否走另一套编译流程。置信度低时使用通用目录驱动策略。

### 2. 新增 DocumentOutlineExtractor

`DocumentOutlineExtractor` 负责生成统一的 `document_outline`：

```json
{
  "document_type": "CONFIG_GUIDE",
  "format": "PDF",
  "title": "BES V9.5.5 配置手册",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "sections": [
    {
      "id": "sec-001",
      "path": "配置文件说明",
      "level": 1,
      "order": 1,
      "page_range": "3-5",
      "char_range": [1200, 5600],
      "required": true,
      "section_type": "REFERENCE"
    },
    {
      "id": "sec-002",
      "path": "连接池配置/最大连接数",
      "level": 2,
      "order": 2,
      "page_range": "6-9",
      "char_range": [5601, 11200],
      "required": true,
      "section_type": "CONFIG_ITEM"
    }
  ]
}
```

结构来源优先级：

1. PDF 书签或目录。
2. 文本中的目录页。
3. Markdown 标题。
4. 编号标题，如 `1.1`、`第 1 章`、`一、`、`（一）`。
5. 视觉/版式线索，如短行标题、前后空行、编号连续性。
6. LLM 目录补全，只作为兜底，并必须保留置信度。

### 3. Word、PDF、Markdown 的 section 拆分方法

不同文件格式的结构信号不同，但最终都要归一成同一个 `document_outline.sections`。

#### Markdown 拆分

Markdown 结构最清晰，应优先使用语法树或 CommonMark 解析，不建议只用正则硬切。

拆分规则：

1. 以 ATX 标题 `#` 到 `######` 和 Setext 标题作为一级结构信号。
2. 标题下连续内容归属当前 section，直到遇到同级或更高级标题。
3. 保留代码块、表格、列表、引用块的完整性，不能把一个代码块或表格切到两个 section。
4. YAML front matter 作为文档元数据，不作为正文 section。
5. 如果 Markdown 没有标题，则用编号段落、粗体短行、列表层级做弱标题识别。
6. `section_path` 由标题栈生成，例如 `部署/启动服务/验证状态`。

Markdown section 输出建议包含：

```json
{
  "id": "sec-010",
  "path": "配置/连接池参数",
  "level": 2,
  "heading_text": "连接池参数",
  "char_range": [3200, 5100],
  "blocks": ["paragraph", "table", "code"],
  "required": true
}
```

#### Word 拆分

Word 文档应优先读取结构化样式，而不是只读取纯文本。

拆分规则：

1. 优先使用段落样式：`Heading 1/2/3`、`标题 1/2/3`、大纲级别。
2. 读取 Word 内置目录字段或超链接目录，提取标题层级和页码参考。
3. 识别编号标题：`1`、`1.1`、`1.1.1`、`第 1 章`、`一、`、`（一）`。
4. 表格不要按单元格拆散，应作为当前 section 的结构块；配置项表格可进一步抽取为 `CONFIG_ITEM` 子 section。
5. 图片、流程图、截图保留占位信息，记录附近标题、图片说明和段落范围。
6. 页眉页脚通常不进正文，但其中的软件名、版本号可作为元数据候选。
7. 如果标题样式混乱，用字号、加粗、居中、编号连续性和前后空行做弱标题判定。

Word section 输出建议包含：

```json
{
  "id": "sec-021",
  "path": "参数说明/线程池配置",
  "level": 2,
  "style": "Heading 2",
  "paragraph_range": [88, 105],
  "page_range": "12-13",
  "blocks": ["paragraph", "table"],
  "required": true
}
```

#### PDF 拆分

PDF 最不稳定，应把“目录/书签/版式/文本编号”组合使用，避免只按空行或固定长度切。

拆分规则：

1. 优先读取 PDF outline/bookmarks，作为章节层级主来源。
2. 如果没有书签，先识别目录页：匹配标题、点线、页码，例如 `2.3 参数配置 .... 15`。
3. 用目录项回填正文页码范围，建立 `section -> page_range`。
4. 对正文页按版式识别标题：字号较大、加粗、短行、编号连续、上下间距明显。
5. 识别页眉页脚和页码，避免把重复页眉页脚当成正文。
6. 表格区域尽量整体保留；参数表、端口表、兼容性表可抽取为子 section。
7. 对双栏、页内换行、断词、OCR 噪声做文本清洗，再做标题判断。
8. 如果目录项找不到正文锚点，保留目录 section，并标记 `confidence=LOW`，交给 LLM 补全和质量门禁处理。

PDF section 输出建议包含：

```json
{
  "id": "sec-034",
  "path": "监控指标/JMX 指标",
  "level": 2,
  "page_range": "28-31",
  "source_signal": "toc+layout",
  "confidence": 0.78,
  "blocks": ["paragraph", "table"],
  "required": true
}
```

#### section 归一化规则

所有格式最终统一成同一个 section 模型：

- `id`：稳定 section id，可由 sourceId + heading path + order hash 生成。
- `path`：标题层级路径。
- `level`：章节层级。
- `order`：文档内顺序。
- `page_range`：PDF/Word 优先提供，Markdown 可为空。
- `char_range` 或 `paragraph_range`：用于回溯原文。
- `blocks`：paragraph/table/list/code/image 等结构块。
- `section_type`：由文档类型和内容识别得到，如 `PROCEDURE`、`CONFIG_ITEM`、`METRIC`、`TROUBLESHOOTING_STEP`。
- `required`：是否必须进入 page_plan。
- `confidence`：结构识别置信度。

`required` 判定不要只看标题层级。不同文档类型应有不同规则：配置手册中的参数表、排障手册中的处理步骤、监控手册中的指标表、标准规范中的强制条款，即使层级较低，也应标为 required。

### 4. 长文档改为 Section Facts

长文档分段不再直接生成 Wiki 页面。每个 section 或 section group 先生成 `section_facts`：

```json
{
  "section_id": "sec-002",
  "section_path": "安装方式/命令行安装",
  "facts": [
    "命令行安装前需要确认 JDK 版本满足要求。",
    "安装过程包含解压、配置环境变量、执行安装脚本和启动验证。"
  ],
  "operations": [
    {
      "step": 1,
      "action": "解压安装包",
      "command": "tar -xf bes-*.tar.gz",
      "evidence": "原文短摘录"
    }
  ],
  "entities": ["BES", "JDK"],
  "warnings": ["安装路径不要包含中文或空格"]
}
```

`section_facts` 只提取事实、步骤、参数、注意事项、依赖和证据，不落库为页面。

### 5. 新增 Page Planner

`Page Planner` 输入全文 `document_outline`、`section_facts`、已有页面摘要和软件分类参考，输出 `page_plan`：

```json
{
  "pages": [
    {
      "planned_title": "BES V9.5.5 连接池配置参数",
      "page_type": "STANDARD",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-001"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["适用范围", "参数说明", "默认值", "配置示例", "生效方式"]
    },
    {
      "planned_title": "BES V9.5.5 连接池配置变更流程",
      "page_type": "RUNBOOK",
      "category": "中间件",
      "software": "BES",
      "version": "V9.5.5",
      "covered_section_ids": ["sec-002", "sec-003"],
      "required": true,
      "merge_strategy": "CREATE_OR_PATCH",
      "expected_outline": ["变更前检查", "修改配置", "重启或热加载", "验证方式", "回滚"]
    }
  ]
}
```

Planner 规则：

- 每个 required section 必须映射到至少一个 page。
- 一个页面可以覆盖多个连续小节，但不能把多个高价值章节压成过短概括页。
- 标题必须包含软件名和版本，避免“安装方式”“产品配置”“参数说明”“问题处理”这类泛标题。
- 页面类型优先服务运维使用场景，步骤类内容优先 `RUNBOOK`。
- 文档类型只影响规划偏好，不改变主链路。

### 6. 按 Page Plan 生成页面

页面生成阶段只处理一个计划页面对应的 facts 和原文证据，输出完整 Wiki 页面：

```json
{
  "title": "BES V9.5.5 安装步骤",
  "page_type": "RUNBOOK",
  "category": "中间件",
  "software": "BES",
  "version": "V9.5.5",
  "summary": "BES V9.5.5 的前置检查、安装、启动、验证和回滚步骤。",
  "content": "...",
  "source_refs": {
    "source_id": 12,
    "source_title": "BES V9.5.5 安装部署手册.pdf",
    "source_type": "UPLOAD",
    "sections": [
      {
        "section_id": "sec-002",
        "section_path": "安装方式/命令行安装",
        "page_range": "6-9"
      }
    ]
  },
  "coverage": {
    "section_ids": ["sec-002", "sec-003"],
    "evidence_quotes": ["原文短摘录，不超过 50 字"]
  },
  "links": [
    {
      "to": "BES V9.5.5 安装环境要求",
      "type": "DEPENDS_ON",
      "confidence": 0.9
    }
  ]
}
```

## P0：质量门禁

新增 `WikiIngestQualityGate`，在页面保存前后生成质量报告：

```json
{
  "source_id": 12,
  "coverage_ratio": 0.86,
  "required_sections_total": 7,
  "required_sections_covered": 6,
  "missing_sections": ["产品注册"],
  "short_pages": ["BES V9.5.5 安装步骤"],
  "generic_titles": ["安装方式"],
  "duplicate_titles": [],
  "pages_without_source_refs": [],
  "status": "PARTIAL"
}
```

门禁规则：

- `coverage_ratio < 0.7`：任务 `FAILED`，不标记 source 已编译。
- `0.7 <= coverage_ratio < 0.9`：任务 `PARTIAL`，页面可保留为 DRAFT，但前端必须显示缺失章节。
- `coverage_ratio >= 0.9`：任务可进入 DRAFT 审核。
- 任何 required section 未覆盖：最高只能是 `PARTIAL`。
- 非 Overview 页面没有 `source_refs.sections`：拒绝保存或标为失败。
- 非 Overview 页面正文少于 300 字：标记 `LOW_CONTENT_QUALITY`。
- 标题不含软件名且属于泛标题：标记 `GENERIC_TITLE`。
- 一个页面覆盖多个 required section 但正文过短：标记 `OVER_COMPRESSED_PAGE`。

## P1：文档类型策略

文档类型策略的作用是指导 section 识别和 page_plan 规划，不是为每种文档类型复制一套流程。所有文档类型都必须先输出 `document_outline.sections`，再根据 section 生成 `page_plan`。

| document_type | required section 判定 | section_facts 重点 | page_plan 重点 |
| --- | --- | --- | --- |
| `PRODUCT_OVERVIEW` | 产品定位、架构、组件、版本、适用范围 | 实体、模块、能力、版本差异 | 产品概览页、核心概念页 |
| `INSTALL_GUIDE` | 环境准备、操作步骤、启动验证、回滚、授权 | 前置条件、命令、步骤、注意事项 | 操作手册页、环境要求页 |
| `CONFIG_GUIDE` | 参数表、配置文件、默认值、生效方式、示例 | 参数名、默认值、单位、依赖关系 | 参数标准页、配置变更流程页 |
| `MONITORING_GUIDE` | 指标表、接入方式、权限、端口、告警规则 | 指标、采集方式、阈值、权限要求 | 监控接入页、指标说明页 |
| `TROUBLESHOOTING` | 故障现象、日志、检查命令、根因、修复、验证 | 症状、检查项、根因、处理步骤 | 排障经验页、操作手册页 |
| `STANDARD_SPEC` | 强制条款、基线要求、适用范围、例外条件 | 条款、等级、约束、检查项 | 标准规范页 |
| `RELEASE_NOTE` | 版本号、变更项、兼容性、修复缺陷、升级影响 | 变更、影响范围、风险、兼容性 | 版本说明页、风险说明页 |

通用规则：

- 原文存在明确章节、表格、条款或步骤时，必须映射到 page_plan。
- 原文没有证据时不能编造；可在质量报告中记录 `not_applicable_topics`。
- 多个小 section 可以合并为一个页面，但必须保留 `coverage.section_ids`。
- 低层级但高价值内容必须标记为 required，例如参数表、指标表、故障处理步骤、强制条款。
- 文档类型置信度低时，按通用目录结构规划，质量门禁仍以 section 覆盖率为准。

## P1：章节级合并

现有合并按 `title + page_type` 找已有页面，再让 LLM 判断 `OVERWRITE / APPEND / CONTRADICT`。这对长文档不稳定，容易把后续分段生成的泛标题页面覆盖前面细节。

目标合并策略：

- 先通过 `canonical_title`、`software`、`version`、`page_type` 定位候选页面。
- 再按 `source_refs.sections` 和 Markdown heading block 做章节级 patch。
- 相同章节更新对应 block，不覆盖其他章节。
- 不同章节追加为新 block。
- 标题相近但覆盖章节不同且主题不同，应创建新页面。
- 合并判断失败时不能默认 `OVERWRITE`，应降级为人工审核或 `APPEND_WITH_REVIEW`。

标题规范化示例：

- `Actuator监控` -> `BES V9.5.5 Actuator 监控`
- `JMX监控` -> `BES V9.5.5 JMX 监控`
- `产品配置` -> `BES V9.5.5 产品配置`
- `安装方式` -> `BES V9.5.5 安装方式`

## P1：向量检索分型优化

### 当前问题

知识库向量导入时 metadata 已写入 `category`、`software`、`sourceType`、`sourceId`，但 `VectorStore.search()` 只有 `topK` 参数，Milvus schema 也只把 metadata 作为 JSON 字符串保存，无法使用表达式过滤。

这会导致：

- “安装失败”“连接超时”“配置参数”这类通用问题跨中间件、数据库、主机混召回。
- 用户在 BES 页面提问时，仍可能召回 MySQL 或主机文档。
- 向量搜索结果只能靠相似度排序，缺少业务上下文约束。

### 推荐方案：单 collection + 标量过滤

优先改为一个逻辑 collection，并增加可过滤字段：

- `category`
- `software`
- `version`
- `source_type`
- `source_id`
- `doc_type`
- `page_type`
- `status`

接口改为：

```java
List<VectorSearchResult> search(float[] queryVector, int topK, VectorSearchFilter filter);
```

Milvus 搜索表达式示例：

```text
category == "中间件" && software == "BES" && status == "ACTIVE"
```

召回策略：

1. 如果用户当前上下文已有 `category/software`，强过滤。
2. 如果 query 明确提到软件名，先做软件识别，再过滤或加权。
3. 如果 query 跨软件或跨分类，如 “BES 连接 MySQL 超时”，允许多路过滤搜索后合并。
4. 如果分类无法判断，先全局召回较大的 topK，再按 metadata rerank。

### 何时考虑拆 collection

只有在以下场景才建议按分类拆 collection 或 partition：

- 单 collection 数据量达到性能瓶颈。
- 不同分类使用不同 embedding 模型或维度。
- 不同分类有强隔离要求。
- Milvus 标量过滤性能不能满足延迟要求。

优先级建议：

1. 单 collection + 标量字段过滤。
2. Milvus partition by category。
3. collection per category。

不建议一开始就拆成中间件、数据库多个 collection，因为查询路由、跨分类问题、迁移、删除、统计和权限过滤都会变复杂。

## P1：图谱社区按软件类型聚类

图谱社区不要拆得过细。当前业务视角下，社区只需要按软件类型或软件维度划分，避免同一个软件被拆成“安装”“配置”“监控”等多个小社区，造成用户误以为知识重复或割裂。

### 社区划分规则

社区主键建议：

```text
community_key = category + "/" + software
```

如果 `software` 为空，则退化为：

```text
community_key = category + "/未指定软件"
```

如果后续引入 `software_type_id`，优先使用稳定 ID：

```text
community_key = category + "/" + software_type_id
```

社区名称建议：

```text
{software} ({category})
```

示例：

- `BES (中间件)`
- `TongWeb V7.0 (中间件)`
- `MySQL (数据库)`

### 图算法定位

Louvain 这类社区检测不再作为主社区划分依据。它可以保留为内部分析能力，但前端默认展示和接口默认返回应使用软件类型社区。

建议：

- 主社区：按 `category + software` 或 `software_type_id` 固定分组。
- 社区内排序：可按页面类型、链接度、更新时间、文档类型排序。
- 社区内标签：可展示安装、配置、监控、排障等 topic，但 topic 不产生新的社区。
- 跨软件边：仍保留，用于展示依赖关系，例如 BES 依赖 MySQL，但不把两个软件合并为同一社区。

### 稳定社区编号

社区编号按稳定业务键排序：

```text
stable_key = category + "/" + software
```

这样同一批数据连续请求不会出现颜色和社区编号跳动。

## P2：前端质量可视化

来源列表和任务详情增加：

- 编译状态。
- 覆盖率百分比。
- required sections 总数和已覆盖数量。
- 缺失章节。
- 短页面。
- 泛化标题。
- 无 source_refs 页面。
- “补编缺失章节”入口。
- “按目录重新规划”入口。

图谱页增加：

- 按软件、分类、页面类型、状态过滤。
- 社区列表显示软件类型社区名称、节点数、边数。
- 空图或低质量图提示具体原因，如 DRAFT 不可见、覆盖率低、链接不足。

## 落地步骤

### 第一步：修主链路

1. [ ] 新增 `DocumentOutlineExtractor`，支持 Markdown、PDF 文本和编号标题。
2. [ ] 新增 `SectionFactExtractor`，长文档分段只产出 facts。
3. [ ] 新增 `PagePlanner`，生成 `page_plan`。
4. [ ] 修改 `IngestAgent`，按 `page_plan` 生成页面。
5. [ ] 修改长文档任务流程，不再每个 chunk 直接落页面。

### 第二步：修质量门禁

1. [ ] 新增 `WikiIngestQualityGate`。
2. [ ] 扩展页面输出 schema，强制 `source_refs.sections` 和 `coverage.section_ids`。
3. [ ] 任务结果记录质量报告。
4. [ ] 低覆盖率任务标记 `PARTIAL/FAILED`，不静默成功。
5. [ ] 前端展示覆盖率和缺失章节。

### 第三步：修合并和 source_refs

1. [ ] 新增 `canonical_title` 和 `alias_titles` 策略。
2. [ ] 合并从整页 overwrite/append 改为 heading block patch。
3. [ ] 合并判断失败时禁止默认 overwrite。
4. [ ] 所有长文档生成路径写入完整 `source_refs`。

### 第四步：修检索

1. [ ] `VectorStore.search()` 增加 `VectorSearchFilter`。
2. [ ] Milvus schema 增加 `category/software/source_type/source_id/status` 等标量字段。
3. [ ] InMemoryVectorStore 支持同样过滤逻辑。
4. [ ] `KnowledgeService.search()` 根据上下文传入过滤条件。
5. [ ] Agent 工具调用支持传入分类、软件和来源范围。

### 第五步：修图谱

1. [ ] 社区划分改为 `category + software` 或 `software_type_id`。
2. [ ] 社区名称固定为 `{software} ({category})`。
3. [ ] 前端默认展示软件类型社区，不再按安装/配置/监控等 topic 拆社区。
4. [ ] 社区编号按稳定业务键排序。
5. [ ] 图谱 API 返回社区 key、社区名称、节点数、边数。

## 长文档验收样例

以“宝兰德应用服务器软件微服务版 V9.5.5”这类长文档为例，重新编译后应达到：

- required sections 覆盖率 >= 90%。
- 每个非 Overview 页面都有 `source_refs.sections`。
- 页面标题包含 `BES` 和 `V9.5.5`。
- 安装环境、安装方式、安装步骤、产品注册、产品配置、Actuator 监控、JMX 监控等原文章节均被覆盖。
- 不能只生成“产品概览、Actuator 监控、JMX 监控”这类少量概念页。
- 图谱只出现一个 `BES (中间件)` 软件类型社区，安装、配置、监控、排障等页面都归入该社区。

期望页面示例：

- BES V9.5.5 产品概览
- BES V9.5.5 安装环境要求
- BES V9.5.5 安装方式
- BES V9.5.5 安装步骤
- BES V9.5.5 产品注册
- BES V9.5.5 产品配置
- BES V9.5.5 Actuator 监控
- BES V9.5.5 JMX 监控
- BES V9.5.5 常见问题

## 测试计划

- 单元测试：目录抽取、编号标题识别、section range 计算、page_plan 校验、覆盖率计算。
- 单元测试：标题规范化、source_refs schema 校验、短页面检测、泛化标题检测。
- 单元测试：VectorSearchFilter 在 Milvus/InMemory 两种实现中的行为一致性。
- 集成测试：固定 BES 文本样例跑完整编译，断言页面数、覆盖率、source_refs 和缺失章节。
- 回归测试：普通短文档仍可直接编译，不被长文档流程过度复杂化。
- 图谱测试：社区名称唯一、稳定，同一软件只归入一个软件类型社区。
- 前端测试：任务详情展示覆盖率、缺失章节和补编入口。

## 优先级建议

最高优先级是“目录抽取 + page_plan + 质量门禁”。这三项直接决定长文档是否能完整编译。

第二优先级是“source_refs 完整化 + 章节级合并”。这能解决页面追溯和分段互相覆盖的问题。

第三优先级是“向量标量过滤 + 图谱软件类型社区”。它们能改善检索和展示体验，但不能替代目录驱动编译。
