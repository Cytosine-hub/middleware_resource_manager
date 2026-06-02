package com.middleware.manager.wiki.web;

import com.middleware.manager.knowledge.loader.DocumentLoader;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.entity.WikiSource;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import com.middleware.manager.wiki.repository.WikiSourceMapper;
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

    private final WikiPageMapper pageMapper;
    private final WikiSourceMapper sourceMapper;
    private final IngestAgent ingestAgent;
    private final WikiExportService exportService;
    private final WikiImportService importService;
    private final List<DocumentLoader> documentLoaders;

    public WikiController(WikiPageMapper pageMapper,
                          WikiSourceMapper sourceMapper,
                          IngestAgent ingestAgent,
                          WikiExportService exportService,
                          WikiImportService importService,
                          List<DocumentLoader> documentLoaders) {
        this.pageMapper = pageMapper;
        this.sourceMapper = sourceMapper;
        this.ingestAgent = ingestAgent;
        this.exportService = exportService;
        this.importService = importService;
        this.documentLoaders = documentLoaders;
    }

    @GetMapping("/pages")
    public List<WikiPage> listPages(@RequestParam(required = false) String category,
                                    @RequestParam(required = false) String software,
                                    @RequestParam(required = false) String status) {
        if (category != null) return pageMapper.findByCategory(category);
        if (software != null) return pageMapper.findBySoftware(software);
        if (status != null) return pageMapper.findByStatus(status);
        return pageMapper.findAll();
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<WikiPage> getPage(@PathVariable Long id) {
        WikiPage page = pageMapper.findById(id);
        return page != null ? ResponseEntity.ok(page) : ResponseEntity.notFound().build();
    }

    @GetMapping("/pages/search")
    public List<WikiPage> searchPages(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        List<WikiPage> results = pageMapper.fulltextSearch(q, limit);
        if (results.isEmpty()) {
            results = pageMapper.findByTitleContaining(q, limit);
        }
        return results;
    }

    @PutMapping("/pages/{id}")
    public ResponseEntity<WikiPage> updatePage(@PathVariable Long id, @RequestBody WikiPage page) {
        WikiPage existing = pageMapper.findById(id);
        if (existing == null) return ResponseEntity.notFound().build();

        if (page.getTitle() != null) existing.setTitle(page.getTitle());
        if (page.getContent() != null) existing.setContent(page.getContent());
        if (page.getSummary() != null) existing.setSummary(page.getSummary());
        if (page.getCategory() != null) existing.setCategory(page.getCategory());
        if (page.getSoftware() != null) existing.setSoftware(page.getSoftware());
        if (page.getVersion() != null) existing.setVersion(page.getVersion());
        if (page.getStatus() != null) existing.setStatus(page.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        pageMapper.update(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id) {
        pageMapper.deleteById(id);
        return ResponseEntity.ok().build();
    }

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

            WikiSource source = sourceMapper.findByContentHash(hash);
            if (source == null) {
                source = new WikiSource();
                source.setTitle(fileName);
                source.setSourceType("UPLOAD");
                source.setContent(content);
                source.setContentHash(hash);
                sourceMapper.insert(source);
            }

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

            if (content == null || content.isBlank()) return ResponseEntity.badRequest().build();

            String hash = sha256(content);

            WikiSource source = sourceMapper.findByContentHash(hash);
            if (source == null) {
                source = new WikiSource();
                source.setTitle(title);
                source.setSourceType("MANUAL");
                source.setContent(content);
                source.setContentHash(hash);
                source.setCategory(category);
                source.setSoftware(software);
                sourceMapper.insert(source);
            }

            IngestAgent.IngestResult result = ingestAgent.ingest(source, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ingest text failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

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

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_pages", pageMapper.findAll().size());
        stats.put("active_pages", pageMapper.countByStatus("ACTIVE"));
        stats.put("draft_pages", pageMapper.countByStatus("DRAFT"));
        stats.put("contradicted_pages", pageMapper.countByStatus("CONTRADICTED"));
        stats.put("stale_pages", pageMapper.countByStatus("STALE"));
        stats.put("total_sources", sourceMapper.findAll().size());
        stats.put("uningested_sources", sourceMapper.findByIngested(false).size());
        return stats;
    }

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
