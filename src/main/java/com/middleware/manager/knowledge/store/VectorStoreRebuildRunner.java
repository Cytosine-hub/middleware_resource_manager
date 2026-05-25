package com.middleware.manager.knowledge.store;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.entity.KnowledgeChunk;
import com.middleware.manager.knowledge.repository.KnowledgeChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时自动重建 InMemoryVectorStore。
 * 从 MySQL knowledge_chunks 表读取已有切片，重新生成 embedding 写入内存向量库。
 * 仅在 app.vector.type=memory 时生效，生产环境（milvus）不执行。
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.vector.type", havingValue = "memory")
public class VectorStoreRebuildRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRebuildRunner.class);

    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public VectorStoreRebuildRunner(KnowledgeChunkRepository chunkRepository,
                                     EmbeddingService embeddingService,
                                     VectorStore vectorStore) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        new Thread(() -> rebuild(), "vector-rebuild").start();
    }

    private void rebuild() {
        try {
            List<KnowledgeChunk> chunks = chunkRepository.findAll();
            if (chunks.isEmpty()) {
                log.info("[VectorRebuild] No chunks in database, skip.");
                return;
            }

            log.info("[VectorRebuild] Found {} chunks, rebuilding vectors...", chunks.size());
            int success = 0;
            int fail = 0;

            for (KnowledgeChunk chunk : chunks) {
                try {
                    float[] vector = embeddingService.embed(chunk.getContent());
                    String vectorId = chunk.getVectorId();
                    if (vectorId == null) {
                        vectorId = java.util.UUID.randomUUID().toString();
                    }

                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("content", chunk.getContent());
                    metadata.put("sourceTitle", chunk.getSourceTitle());
                    metadata.put("chunkIndex", String.valueOf(chunk.getChunkIndex()));
                    if (chunk.getSourceType() != null) {
                        metadata.put("sourceType", chunk.getSourceType());
                    }
                    if (chunk.getSourceId() != null) {
                        metadata.put("sourceId", String.valueOf(chunk.getSourceId()));
                    }

                    vectorStore.add(vectorId, vector, metadata);
                    success++;
                } catch (Exception e) {
                    fail++;
                    log.warn("[VectorRebuild] Failed to embed chunk {}: {}", chunk.getId(), e.getMessage());
                }
            }

            log.info("[VectorRebuild] Done. success={}, fail={}, total={}", success, fail, chunks.size());
        } catch (Exception e) {
            log.error("[VectorRebuild] Rebuild failed: {}", e.getMessage(), e);
        }
    }
}
