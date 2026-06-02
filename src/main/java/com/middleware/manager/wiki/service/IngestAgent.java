package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.entity.WikiIngestLog;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiIngestLogRepository;
import com.middleware.manager.wiki.repository.WikiPageRepository;
import com.middleware.manager.wiki.repository.WikiSourceRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Ingest Agent：两步 Chain-of-Thought 编译流程。
 * Step 1: 结构化分析（提取实体、概念、关系、矛盾）
 * Step 2: Wiki 页面生成（基于分析结果生成结构化 Markdown）
 */
@Service
public class IngestAgent {

    private static final Logger log = LoggerFactory.getLogger(IngestAgent.class);
    private static final int MAX_RETRIES = 3;

    private final ChatModel chatModel;
    private final WikiPageRepository pageRepo;
    private final WikiSourceRepository sourceRepo;
    private final WikiIngestLogRepository logRepo;
    private final LinkResolver linkResolver;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final Gson gson = new Gson();

    @Value("${app.vector.type:milvus}")
    private String vectorType;

    public IngestAgent(ChatModel chatModel,
                       WikiPageRepository pageRepo,
                       WikiSourceRepository sourceRepo,
                       WikiIngestLogRepository logRepo,
                       LinkResolver linkResolver,
                       EmbeddingService embeddingService,
                       VectorStore vectorStore) {
        this.chatModel = chatModel;
        this.pageRepo = pageRepo;
        this.sourceRepo = sourceRepo;
        this.logRepo = logRepo;
        this.linkResolver = linkResolver;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 编译结果 DTO。
     */
    public static class IngestResult {
        private int pagesCreated;
        private int pagesUpdated;
        private int linksCreated;
        private int contradictionsFound;
        private String status;

        public int getPagesCreated() { return pagesCreated; }
        public int getPagesUpdated() { return pagesUpdated; }
        public int getLinksCreated() { return linksCreated; }
        public int getContradictionsFound() { return contradictionsFound; }
        public String getStatus() { return status; }

        public void setPagesCreated(int v) { this.pagesCreated = v; }
        public void setPagesUpdated(int v) { this.pagesUpdated = v; }
        public void setLinksCreated(int v) { this.linksCreated = v; }
        public void setContradictionsFound(int v) { this.contradictionsFound = v; }
        public void setStatus(String v) { this.status = v; }
    }

    /**
     * 执行完整的 Ingest 编译流程。
     *
     * @param source    原始文档
     * @param operatorId 操作人 ID
     * @return 编译结果
     */
    public IngestResult ingest(WikiSource source, Long operatorId) {
        long startTime = System.currentTimeMillis();
        IngestResult result = new IngestResult();
        WikiIngestLog ingestLog = new WikiIngestLog();
        ingestLog.setSourceId(source.getId());
        ingestLog.setOperatorId(operatorId);

        try {
            // 1. 增量判断
            String hash = sha256(source.getContent());
            if (hash.equals(source.getContentHash()) && source.isIngested()) {
                log.info("Source '{}' content unchanged, skipping ingest", source.getTitle());
                result.setStatus("SKIPPED");
                return result;
            }

            // 2. 获取已有页面摘要（用于矛盾检测）
            String existingSummary = buildExistingPagesSummary(source.getCategory(), source.getSoftware());

            // 3. Step 1: 结构化分析
            log.info("Ingest Step 1: Analyzing source '{}'", source.getTitle());
            String analysisPrompt = IngestPromptTemplates.buildAnalysisPrompt(source.getContent(), existingSummary);
            String analysisJson = callLlm(analysisPrompt);
            ingestLog.setLlmModel("configured");

            // 4. 验证分析结果
            JsonObject analysis = parseJson(analysisJson);
            if (analysis == null) {
                log.error("Failed to parse analysis JSON for source '{}'", source.getTitle());
                result.setStatus("FAILED");
                ingestLog.setStatus("FAILED");
                ingestLog.setErrorDetail("Failed to parse analysis JSON");
                ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
                logRepo.save(ingestLog);
                return result;
            }

            // 5. Step 2: Wiki 页面生成
            log.info("Ingest Step 2: Generating Wiki pages for source '{}'", source.getTitle());
            String generationPrompt = IngestPromptTemplates.buildPageGenerationPrompt(source.getContent(), analysisJson);
            String pagesJson = callLlm(generationPrompt);

            // 6. 解析并保存页面
            JsonObject pagesResult = parseJson(pagesJson);
            if (pagesResult == null || !pagesResult.has("pages")) {
                log.error("Failed to parse pages JSON for source '{}'", source.getTitle());
                result.setStatus("FAILED");
                ingestLog.setStatus("FAILED");
                ingestLog.setErrorDetail("Failed to parse pages JSON");
                ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
                logRepo.save(ingestLog);
                return result;
            }

            int created = 0, updated = 0, contradictions = 0;
            List<WikiPage> savedPages = new ArrayList<>();
            JsonArray pages = pagesResult.getAsJsonArray("pages");

            for (JsonElement elem : pages) {
                JsonObject pageObj = elem.getAsJsonObject();
                String title = getAsString(pageObj, "title");
                String pageType = getAsString(pageObj, "page_type");

                if (title == null || pageType == null) {
                    log.warn("Skipping page with missing title or page_type");
                    continue;
                }

                // 检查是否已存在
                Optional<WikiPage> existing = pageRepo.findByTitleAndType(title, pageType);
                if (existing.isPresent()) {
                    // 合并决策
                    WikiPage existingPage = existing.get();
                    String mergeDecision = callLlm(IngestPromptTemplates.buildMergeDecisionPrompt(
                        existingPage.getContent(), getAsString(pageObj, "content")));
                    JsonObject decision = parseJson(mergeDecision);
                    String action = decision != null ? getAsString(decision, "action") : "APPEND";

                    if ("CONTRADICT".equals(action)) {
                        existingPage.setStatus("CONTRADICTED");
                        existingPage.setContradictionNote(decision != null ? getAsString(decision, "reason") : "与新文档内容矛盾");
                        pageRepo.save(existingPage);
                        contradictions++;
                    } else if ("OVERWRITE".equals(action)) {
                        updatePageFromJson(existingPage, pageObj, source);
                        pageRepo.save(existingPage);
                        updated++;
                        savedPages.add(existingPage);
                    } else {
                        // APPEND
                        String newContent = getAsString(pageObj, "content");
                        if (newContent != null) {
                            existingPage.setContent(existingPage.getContent() + "\n\n---\n\n" + newContent);
                        }
                        String newSummary = getAsString(pageObj, "summary");
                        if (newSummary != null) {
                            existingPage.setSummary(newSummary);
                        }
                        existingPage.setCompiledBy("ingest-agent");
                        existingPage.setCompiledAt(LocalDateTime.now());
                        pageRepo.save(existingPage);
                        updated++;
                        savedPages.add(existingPage);
                    }
                } else {
                    // 新页面
                    WikiPage newPage = new WikiPage();
                    updatePageFromJson(newPage, pageObj, source);
                    pageRepo.save(newPage);
                    created++;
                    savedPages.add(newPage);
                }
            }

            // 7. 建立交叉引用
            int linksCreated = linkResolver.resolveLinks(savedPages);

            // 8. 向量化（可选）
            if ("milvus".equals(vectorType)) {
                try {
                    for (WikiPage page : savedPages) {
                        String text = page.getTitle() + "\n" + (page.getSummary() != null ? page.getSummary() : "");
                        float[] vector = embeddingService.embed(text);
                        String vectorId = "wiki_" + page.getId();
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("pageId", String.valueOf(page.getId()));
                        metadata.put("title", page.getTitle());
                        metadata.put("pageType", page.getPageType());
                        vectorStore.add(vectorId, vector, metadata);
                    }
                } catch (Exception e) {
                    log.warn("Vectorization failed (Milvus may not be available): {}", e.getMessage());
                }
            }

            // 9. 标记源文档已编译
            source.setContentHash(hash);
            source.setIngested(true);
            source.setIngestedAt(LocalDateTime.now());
            sourceRepo.save(source);

            // 10. 记录编译日志
            result.setPagesCreated(created);
            result.setPagesUpdated(updated);
            result.setLinksCreated(linksCreated);
            result.setContradictionsFound(contradictions);
            result.setStatus("SUCCESS");

            ingestLog.setPagesCreated(created);
            ingestLog.setPagesUpdated(updated);
            ingestLog.setLinksCreated(linksCreated);
            ingestLog.setContradictionsFound(contradictions);
            ingestLog.setStatus("SUCCESS");
            ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
            logRepo.save(ingestLog);

            log.info("Ingest completed for '{}': created={}, updated={}, links={}, contradictions={}",
                source.getTitle(), created, updated, linksCreated, contradictions);

        } catch (Exception e) {
            log.error("Ingest failed for source '{}': {}", source.getTitle(), e.getMessage(), e);
            result.setStatus("FAILED");
            ingestLog.setStatus("FAILED");
            ingestLog.setErrorDetail(e.getMessage());
            ingestLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
            logRepo.save(ingestLog);
        }

        return result;
    }

    /**
     * 调用 LLM，带重试。
     */
    private String callLlm(String prompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个严格的知识编译器。只输出要求的格式，不要包含任何其他文字。"));
        messages.add(new UserMessage(prompt));

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ChatResponse response = chatModel.chat(messages);
                AiMessage aiMessage = response.aiMessage();
                return aiMessage != null ? aiMessage.text() : "";
            } catch (Exception e) {
                log.warn("LLM call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw new RuntimeException("LLM call failed after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * 安全解析 JSON，处理 LLM 输出中的 markdown 代码块。
     */
    private JsonObject parseJson(String text) {
        if (text == null) return null;
        // 去除可能的 markdown 代码块标记
        text = text.trim();
        if (text.startsWith("```json")) text = text.substring(7);
        if (text.startsWith("```")) text = text.substring(3);
        if (text.endsWith("```")) text = text.substring(0, text.length() - 3);
        text = text.trim();
        try {
            return gson.fromJson(text, JsonObject.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private String getAsString(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        return elem != null && !elem.isJsonNull() ? elem.getAsString() : null;
    }

    private void updatePageFromJson(WikiPage page, JsonObject json, WikiSource source) {
        page.setTitle(getAsString(json, "title"));
        page.setPageType(getAsString(json, "page_type"));
        page.setCategory(getAsString(json, "category"));
        if (page.getCategory() == null) page.setCategory(source.getCategory());
        page.setSoftware(getAsString(json, "software"));
        if (page.getSoftware() == null) page.setSoftware(source.getSoftware());
        page.setVersion(getAsString(json, "version"));
        page.setContent(getAsString(json, "content"));
        page.setSummary(getAsString(json, "summary"));
        page.setStatus("DRAFT");
        page.setCompiledBy("ingest-agent");
        page.setCompiledAt(LocalDateTime.now());
        // source_refs
        JsonObject ref = new JsonObject();
        ref.addProperty("title", source.getTitle());
        ref.addProperty("type", source.getSourceType());
        ref.addProperty("id", source.getId());
        page.setSourceRefs(gson.toJson(ref));
    }

    private String buildExistingPagesSummary(String category, String software) {
        List<WikiPage> pages = pageRepo.findAll();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (WikiPage p : pages) {
            if (count >= 20) break; // 限制上下文长度
            if (category != null && category.equals(p.getCategory()) ||
                software != null && software.equals(p.getSoftware())) {
                sb.append("- ").append(p.getTitle());
                if (p.getSummary() != null) sb.append("：").append(p.getSummary());
                sb.append("\n");
                count++;
            }
        }
        return sb.length() > 0 ? sb.toString() : "（暂无相关页面）";
    }

    /**
     * 计算 SHA-256 哈希。
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }
}
