package com.middleware.manager.wiki.web;

import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageRepository;
import com.middleware.manager.wiki.repository.WikiSourceRepository;
import com.middleware.manager.wiki.service.IngestAgent;
import com.middleware.manager.wiki.service.WikiExportService;
import com.middleware.manager.wiki.service.WikiImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/wiki")
public class WikiController {

    private static final Logger log = LoggerFactory.getLogger(WikiController.class);

    private final WikiPageRepository pageRepo;
    private final WikiSourceRepository sourceRepo;
    private final IngestAgent ingestAgent;
    private final WikiExportService exportService;
    private final WikiImportService importService;
    private final List<DocumentLoader> documentLoaders;

    public WikiController(WikiPageRepository pageRepo,
                          WikiSourceRepository sourceRepo,
                          IngestAgent ingestAgent,
                          WikiExportService exportService,
                          WikiImportService importService,
                          List<DocumentLoader> documentLoaders) {
        this.pageRepo = pageRepo;
        this.sourceRepo = sourceRepo;
        this.ingestAgent = ingestAgent;
        this.exportService = exportService;
        this.importService = importService;
        this.documentLoaders = documentLoaders;
    }

    // ==================== Wiki 页面 CRUD ====================

    @GetMapping("/pages")
    public List<WikiPage> listPages(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String software,
                                    @RequestParam(required = false) String status) {
        if (category != null) return pageRepo.findByCategory(category);
        if (software != null) return pageRepo.findBySoftware(software);
        if (status != null) return pageRepo.findByStatus(status);
        return pageRepo.findAll();
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<WikiPage> getPage(@PathVariable Long id) {
        return pageRepo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/pages/search")
    public List<WikiPage> searchPages(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        // 先尝试 FULLTEXT 搜索
        List<WikiPage> results = pageRepo.fulltextSearch(q, limit);
        if (results.isEmpty()) {
            // fallback 到 LIKE 搜索
            results = pageRepo.findByTitleContaining(q, limit);
        }
        return results;
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<WikiPage> updatePage(@PathVariable Long id, @RequestBody WikiPage page) {
        return pageRepo.findById(id).map(existing -> {
            if (page.getTitle() != null) existing.setTitle(page.getTitle());
            if (page.getContent() != null) existing.setContent(page.getContent());
            if (page.getSummary() != null) existing.setSummary(page.getSummary());
            if (page.getCategory() != null) existing.setCategory(page.getCategory());
            if (page.getSoftware() != null) existing.setSoftware(page.getSoftware());
            if (page.getVersion() != null) existing.setVersion(page.getVersion());
            if (page.getStatus() != null) existing.setStatus(page.getStatus());
            existing.setUpdatedAt(LocalDateTime.now());
            pageRepo.save(existing);
            return ResponseEntity.ok(existing);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ==================== Ingest 编译 ====================

    @PostMapping("/ingest/upload")
    public ResponseEntity<IngestAgent.IngestResult> ingestUpload(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            DocumentLoader loader = resolveLoader(fileName);
            byte[] fileBytes = file.getBytes();

            String content;
            try (InputStream is = new java.io.ByteArrayInputStream(fileBytes)) {
                content = loader.load(is, fileName);
            }

            String hash = sha256(content);

            // 创建或更新 WikiSource
            WikiSource source = sourceRepo.findByContentHash(hash).orElseGet(() -> {
                WikiSource s = new WikiSource();
                s.setTitle(fileName);
                s.setSourceType("UPLOAD");
                s.setContent(content);
                s.setContentHash(hash);
                return sourceRepo.save(s);
            });

            // 执行编译
            IngestAgent.IngestResult result = ingestAgent.ingest(source, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ingest upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/text")
    public ResponseEntity<IngestAgent.IngestResult> ingestText(@RequestBody Map<String, String> body) {
        try {
            String title = body.getOrDefault("title", "未命名文档");
            String content = body.get("content");
            String category = body.get("category");
            String software = body.get("software");

            if (content == null || content.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            String hash = sha256(content);

            WikiSource source = sourceRepo.findByContentHash(hash).orElseGet(() -> {
                WikiSource s = new WikiSource();
                s.setTitle(title);
                s.setSourceType("MANUAL");
                s.setContent(content);
                s.setContentHash(hash);
                s.setCategory(category);
                s.setSoftware(software);
                return sourceRepo.save(s);
            });

            IngestAgent.IngestResult result = ingestAgent.ingest(source, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ingest text failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 导入导出 ====================

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportWiki(@RequestParam(required = false) String category) {
        try {
            byte[] zipBytes = (category != null) ? exportService.exportByCategory(category) : exportService.exportAll();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=wiki-export.zip")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zipBytes);
        } catch (Exception e) {
            log.error("Export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<WikiImportService.ImportResult> importWiki(@RequestParam("file") MultipartFile file) {
        try {
            WikiImportService.ImportResult result = importService.importFromZip(file.getBytes());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ==================== 统计 ====================

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_pages", pageRepo.findAll().size());
        stats.put("active_pages", pageRepo.countByStatus("ACTIVE"));
        stats.put("draft_pages", pageRepo.countByStatus("DRAFT"));
        stats.put("contradicted_pages", pageRepo.countByStatus("CONTRADICTED"));
        stats.put("stale_pages", pageRepo.countByStatus("STALE"));
        stats.put("total_sources", sourceRepo.findAll().size());
        stats.put("uningested_sources", sourceRepo.findByIngested(false).size());
        return stats;
    }

    // ==================== 辅助方法 ====================

    private DocumentLoader resolveLoader(String fileName) {
        for (DocumentLoader loader : documentLoaders) {
            if (loader.supports(fileName)) return loader;
        }
        throw new IllegalArgumentException("No document loader found for file: " + fileName);
    }

    private static String sha256(String input) {
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
