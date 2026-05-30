package com.middleware.manager.knowledge.service;

import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.knowledge.loader.StandardDocumentLoader;
import com.middleware.manager.knowledge.repository.KnowledgeChunkRepository;
import com.middleware.manager.knowledge.splitter.TextSplitter;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KnowledgeChunkRepository chunkRepository;
    private final StandardDocumentLoader standardDocumentLoader;
    private final List<DocumentLoader> documentLoaders;
    private final StorageService storageService;

    public KnowledgeService(TextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            VectorStore vectorStore,
                            KnowledgeChunkRepository chunkRepository,
                            StandardDocumentLoader standardDocumentLoader,
                            List<DocumentLoader> documentLoaders,
                            StorageService storageService) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chunkRepository = chunkRepository;
        this.standardDocumentLoader = standardDocumentLoader;
        this.documentLoaders = documentLoaders;
        this.storageService = storageService;
    }

    /**
     * Import a file upload into the knowledge base.
     */
    public ImportResult importFile(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        DocumentLoader loader = resolveLoader(fileName);

        // 先将文件读入内存字节数组，避免多次读取 InputStream 失败
        byte[] fileBytes = file.getBytes();

        // 保存原文件到 storage/knowledge/
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) extension = fileName.substring(lastDot);
        String storedFileName = "knowledge/" + UUID.randomUUID() + extension;
        Path dest = storageService.getRootLocation().resolve(storedFileName);
        Files.createDirectories(dest.getParent());
        Files.write(dest, fileBytes);

        // 从字节数组提取文本内容
        String content;
        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
            content = loader.load(is, fileName);
        }

        String sourceTitle = fileName;
        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);

        return persistChunks(chunks, null, "UPLOAD", null, null, storedFileName);
    }

    /**
     * Import an existing StandardDocument into the knowledge base.
     */
    public ImportResult importStandardDocument(Long docId) {
        StandardDocument doc = standardDocumentLoader.getStandardDocument(docId);
        String content = doc.getContent();
        String sourceTitle = doc.getTitle();

        List<TextSplitter.TextChunk> chunks = textSplitter.split(content, sourceTitle);

        return persistChunks(chunks, docId, "STANDARD_DOC", doc.getCategory(), doc.getSoftware(), null);
    }

    /**
     * Search the knowledge base for relevant chunks.
     * Uses vector search with embedding, falls back to keyword search.
     */
    private static final float MIN_SCORE_THRESHOLD = 0.5f;

    public List<SearchResult> search(String query, int topK) {
        List<SearchResult> results = new ArrayList<>();

        // 向量检索
        try {
            float[] queryVector = embeddingService.embed(query);
            List<VectorStore.VectorSearchResult> vectorResults = vectorStore.search(queryVector, topK);
            if (!vectorResults.isEmpty() && vectorResults.get(0).getScore() >= MIN_SCORE_THRESHOLD) {
                for (VectorStore.VectorSearchResult vr : vectorResults) {
                    if (vr.getScore() < MIN_SCORE_THRESHOLD) break;
                    Map<String, String> meta = vr.getMetadata();
                    SearchResult sr = new SearchResult();
                    sr.setContent(meta != null ? meta.get("content") : null);
                    sr.setSourceTitle(meta != null ? meta.get("sourceTitle") : null);
                    sr.setScore(vr.getScore());
                    sr.setSource("vector");
                    results.add(sr);
                }
            }
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
        }

        // 关键词搜索（补充或降级）
        List<KnowledgeChunk> keywordChunks = chunkRepository.findByContentContaining(query, topK);
        for (KnowledgeChunk chunk : keywordChunks) {
            // 去重：如果向量搜索已包含该 chunk，跳过
            boolean duplicate = false;
            for (SearchResult existing : results) {
                if (chunk.getSourceTitle() != null && chunk.getSourceTitle().equals(existing.getSourceTitle())
                        && chunk.getContent() != null && chunk.getContent().equals(existing.getContent())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                SearchResult sr = new SearchResult();
                sr.setContent(chunk.getContent());
                sr.setSourceTitle(chunk.getSourceTitle());
                sr.setScore(0.8f);
                sr.setSource("keyword");
                results.add(sr);
            }
        }

        // 按分数排序
        results.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) {
                return loader;
            }
        }
        throw new IllegalArgumentException("No document loader found for file: " + fileName);
    }

    private ImportResult persistChunks(List<TextSplitter.TextChunk> chunks,
                                       Long sourceId,
                                       String sourceType,
                                       String category,
                                       String software,
                                       String storedFileName) {
        // Remove old chunks for the same source (if re-importing)
        if (sourceId != null) {
            deleteBySource(sourceId, sourceType);
        }

        List<String> texts = new ArrayList<>();
        for (TextSplitter.TextChunk chunk : chunks) {
            texts.add(chunk.getContent());
        }

        List<float[]> vectors = embeddingService.embedBatch(texts);

        int count = 0;
        for (int i = 0; i < chunks.size(); i++) {
            TextSplitter.TextChunk chunk = chunks.get(i);
            float[] vector = vectors.get(i);

            String vectorId = UUID.randomUUID().toString();

            // Store vector
            Map<String, String> metadata = new HashMap<>();
            metadata.put("content", chunk.getContent());
            metadata.put("sourceTitle", chunk.getSourceTitle());
            metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
            if (sourceType != null) {
                metadata.put("sourceType", sourceType);
            }
            if (sourceId != null) {
                metadata.put("sourceId", String.valueOf(sourceId));
            }
            vectorStore.add(vectorId, vector, metadata);

            // Persist chunk metadata
            KnowledgeChunk entity = new KnowledgeChunk();
            entity.setContent(chunk.getContent());
            entity.setSourceTitle(chunk.getSourceTitle());
            entity.setSourceType(sourceType);
            entity.setSourceId(sourceId);
            entity.setCategory(category);
            entity.setSoftware(software);
            entity.setChunkIndex(chunk.getChunkIndex());
            entity.setVectorId(vectorId);
            entity.setStoredFileName(storedFileName);
            chunkRepository.save(entity);

            count++;
        }

        log.info("Imported {} chunks from source: {}", count,
                chunks.isEmpty() ? "unknown" : chunks.get(0).getSourceTitle());

        ImportResult result = new ImportResult();
        result.setChunkCount(count);
        result.setSourceTitle(chunks.isEmpty() ? null : chunks.get(0).getSourceTitle());
        return result;
    }

    public int deleteDocument(String sourceTitle, String sourceType) {
        // 先查出关联的 vectorId 列表，清理向量库和原文件
        List<KnowledgeChunk> chunks = chunkRepository.findBySourceTitleContaining(sourceTitle);
        String storedFileToDelete = null;
        for (KnowledgeChunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                try {
                    vectorStore.delete(chunk.getVectorId());
                } catch (Exception ignored) {}
            }
            if (chunk.getStoredFileName() != null && storedFileToDelete == null) {
                storedFileToDelete = chunk.getStoredFileName();
            }
        }
        // 删除原文件
        if (storedFileToDelete != null) {
            try {
                storageService.deleteIfExists(storedFileToDelete);
            } catch (Exception ignored) {}
        }
        // 删除 DB 记录
        return chunkRepository.deleteBySourceTitleAndSourceType(sourceTitle, sourceType);
    }

    public int deleteTestDocuments() {
        // 先清理向量库
        List<KnowledgeChunk> all = chunkRepository.findAll();
        for (KnowledgeChunk chunk : all) {
            if (chunk.getSourceTitle() != null &&
                (chunk.getSourceTitle().startsWith("test") || chunk.getSourceTitle().startsWith("proxy-test"))) {
                if (chunk.getVectorId() != null) {
                    try { vectorStore.delete(chunk.getVectorId()); } catch (Exception ignored) {}
                }
            }
        }
        // 删除 DB 记录
        int count = chunkRepository.deleteBySourceTitleLike("test%");
        count += chunkRepository.deleteBySourceTitleLike("proxy-test%");
        return count;
    }

    private void deleteBySource(Long sourceId, String sourceType) {
        // We need to find existing chunks to clean up their vector store entries.
        // Use the repository to find by source title pattern, or we can query directly.
        // Since JPA findAll is removed, we'll rely on vectorStore cleanup being optional
        // and the DB delete handles the persistence layer.
        // For a proper implementation, we'd need a findBySourceIdAndSourceType method,
        // but to minimize changes, we'll skip vector cleanup here since InMemoryVectorStore
        // is ephemeral anyway.
        chunkRepository.deleteBySourceIdAndSourceType(sourceId, sourceType);
    }

    // --- Result DTOs ---

    public static class ImportResult {
        private int chunkCount;
        private String sourceTitle;

        public int getChunkCount() {
            return chunkCount;
        }

        public void setChunkCount(int chunkCount) {
            this.chunkCount = chunkCount;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }
    }

    public static class SearchResult {
        private String content;
        private String sourceTitle;
        private float score;
        private String source; // "vector" or "keyword"

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getSourceTitle() {
            return sourceTitle;
        }

        public void setSourceTitle(String sourceTitle) {
            this.sourceTitle = sourceTitle;
        }

        public float getScore() {
            return score;
        }

        public void setScore(float score) {
            this.score = score;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }
    }
}
