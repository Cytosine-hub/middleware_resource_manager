package com.middleware.manager.wiki.service;

/**
 * Ingest 编译的两步 Chain-of-Thought Prompt 模板。
 * Step 1: 结构化分析（提取实体、概念、关系、矛盾）
 * Step 2: Wiki 页面生成（基于分析结果生成结构化 Markdown）
 *
 * 使用 {@code {{PLACEHOLDER}}} 占位符替代 String.formatted()，
 * 避免用户内容中的 %s/%d 等格式化字符被错误消费。
 */
public class IngestPromptTemplates {

    /**
     * Step 1: 结构化分析 Prompt
     */
    public static String buildAnalysisPrompt(String documentContent, String existingPagesSummary, String softwareReference) {
        return """
            你是银行基础架构运维知识库的编译器。分析以下文档，提取结构化知识。

            ## 输入文档
            {{DOCUMENT_CONTENT}}

            ## 已有 Wiki 页面（用于矛盾检测）
            {{EXISTING_PAGES}}

            ## 已知软件分类参考（必须严格参照此表确定 category）
            {{SOFTWARE_REFERENCE}}

            ## 输出规则
            1. 只提取文档中明确提到的实体，不要推断
            2. 版本号必须精确提取，不要省略
            3. 矛盾检测：如果新文档的建议与已有页面冲突，必须标记，即使你不确定
            4. category 必须从参考表中匹配，如果文档中的软件不在表中，根据软件性质推断最合适的分类
            5. 输出必须是合法 JSON，不要包含任何其他文字

            ## 输出 JSON 格式
            {
              "entities": [
                {
                  "name": "实体名称（含版本号）",
                  "type": "software/infrastructure/system",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名",
                  "version": "版本号",
                  "facts": ["关键事实1", "关键事实2"]
                }
              ],
              "concepts": [
                {
                  "name": "概念名称",
                  "description": "概念描述",
                  "related": ["相关实体1", "相关实体2"]
                }
              ],
              "dependencies": [
                {
                  "from": "源",
                  "to": "目标",
                  "type": "DEPENDS_ON/REFERENCES/RELATED"
                }
              ],
              "contradictions": [
                {
                  "existing_page": "已有页面标题",
                  "conflict": "冲突描述",
                  "reason": "原因分析",
                  "confidence": 0.8
                }
              ]
            }
            """.replace("{{DOCUMENT_CONTENT}}", documentContent)
              .replace("{{EXISTING_PAGES}}", existingPagesSummary)
              .replace("{{SOFTWARE_REFERENCE}}", softwareReference);
    }

    /**
     * Step 2: Wiki 页面生成 Prompt
     */
    public static String buildPageGenerationPrompt(String documentContent, String analysisJson) {
        return """
            你是银行基础架构运维知识库的编译器。根据以下分析结果，生成 Wiki 页面。

            ## 原始文档
            {{DOCUMENT_CONTENT}}

            ## 分析结果
            {{ANALYSIS_JSON}}

            ## 页面生成规则
            1. 每个实体/概念生成一个独立的 Wiki 页面
            2. 页面内容使用 Markdown 格式
            3. 用 [[页面名]] 标记交叉引用（引用其他实体或概念）
            4. 每个页面必须包含：标题、摘要（一句话）、正文
            5. 正文结构：概述 → 关键配置/要点 → 常见问题 → 关联知识
            6. 如果发现矛盾，在页面顶部用 > ⚠️ 矛盾提示 标注

            ## 输出 JSON 格式
            {
              "pages": [
                {
                  "title": "页面标题",
                  "page_type": "ENTITY/CONCEPT/RUNBOOK/EXPERIENCE/STANDARD",
                  "category": "中间件/数据库/主机/网络/安全",
                  "software": "软件名（如有）",
                  "version": "版本号（如有）",
                  "summary": "一句话摘要",
                  "content": "完整的 Markdown 内容，含 [[wikilink]] 交叉引用"
                }
              ],
              "source_summary": {
                "title": "原始文档摘要",
                "content": "文档内容的简要概述"
              }
            }
            """.replace("{{DOCUMENT_CONTENT}}", documentContent)
              .replace("{{ANALYSIS_JSON}}", analysisJson);
    }

    /**
     * 合并决策 Prompt
     */
    public static String buildMergeDecisionPrompt(String existingContent, String newContent) {
        return """
            你是知识库合并决策器。判断新内容与已有页面的关系。

            ## 已有页面内容
            {{EXISTING_CONTENT}}

            ## 新内容
            {{NEW_CONTENT}}

            ## 判断规则
            1. OVERWRITE: 新内容完全替代旧内容（旧内容已过时或有误）
            2. APPEND: 新内容是旧内容的补充（不冲突，追加即可）
            3. CONTRADICT: 新旧内容存在矛盾（需要人工裁决）

            ## 输出 JSON 格式
            {
              "action": "OVERWRITE/APPEND/CONTRADICT",
              "reason": "决策原因"
            }
            """.replace("{{EXISTING_CONTENT}}", existingContent)
              .replace("{{NEW_CONTENT}}", newContent);
    }
}
