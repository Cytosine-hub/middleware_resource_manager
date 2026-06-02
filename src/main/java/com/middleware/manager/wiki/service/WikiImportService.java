package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkRepository;
import com.middleware.manager.wiki.repository.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Wiki 导入服务：解析导出 ZIP 包，写入数据库。
 */
@Service
public class WikiImportService {

    private static final Logger log = LoggerFactory.getLogger(WikiImportService.class);
    private final Gson gson = new Gson();

    private final WikiPageRepository pageRepo;
    private final WikiLinkRepository linkRepo;

    public WikiImportService(WikiPageRepository pageRepo, WikiLinkRepository linkRepo) {
        this.pageRepo = pageRepo;
        this.linkRepo = linkRepo;
    }

    /**
     * 导入结果 DTO。
     */
    public static class ImportResult {
        private int pagesCreated;
        private int pagesUpdated;
        private int linksCreated;
        private int conflicts;
        private String status;

        public int getPagesCreated() { return pagesCreated; }
        public int getPagesUpdated() { return pagesUpdated; }
        public int getLinksCreated() { return linksCreated; }
        public int getConflicts() { return conflicts; }
        public String getStatus() { return status; }

        public void setPagesCreated(int v) { this.pagesCreated = v; }
        public void setPagesUpdated(int v) { this.pagesUpdated = v; }
        public void setLinksCreated(int v) { this.linksCreated = v; }
        public void setConflicts(int v) { this.conflicts = v; }
        public void setStatus(String v) { this.status = v; }
    }

    /**
     * 从 ZIP 字节数组导入 Wiki 页面。
     */
    public ImportResult importFromZip(byte[] zipBytes) throws IOException {
        ImportResult result = new ImportResult();
        Map<String, WikiPage> importedPages = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                String content = readEntry(zis);

                if (name.equals("manifest.json")) {
                    log.info("Import manifest: {}", content.substring(0, Math.min(200, content.length())));
                } else if (name.startsWith("pages/") && name.endsWith(".md")) {
                    WikiPage page = parsePageMarkdown(content);
                    if (page != null) {
                        importedPages.put(page.getTitle(), page);
                    }
                }
                zis.closeEntry();
            }
        }

        // 保存页面
        int created = 0, updated = 0, conflicts = 0;
        for (WikiPage page : importedPages.values()) {
            Optional<WikiPage> existing = pageRepo.findByTitleAndType(page.getTitle(), page.getPageType());
            if (existing.isPresent()) {
                // 标记为冲突（CONTRADICTED），等待人工裁决
                WikiPage existingPage = existing.get();
                existingPage.setStatus("CONTRADICTED");
                existingPage.setContradictionNote("与导入包冲突，待审核");
                pageRepo.save(existingPage);
                conflicts++;
            } else {
                page.setStatus("DRAFT"); // 新页面标为草稿
                pageRepo.save(page);
                created++;
            }
        }

        // 重新读取 ZIP 处理链接
        int linksCreated = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("links.json")) {
                    String linksJson = readEntry(zis);
                    linksCreated = importLinks(linksJson, importedPages);
                }
                zis.closeEntry();
            }
        }

        result.setPagesCreated(created);
        result.setPagesUpdated(updated);
        result.setLinksCreated(linksCreated);
        result.setConflicts(conflicts);
        result.setStatus("SUCCESS");

        log.info("Import completed: created={}, updated={}, links={}, conflicts={}", created, updated, linksCreated, conflicts);
        return result;
    }

    private WikiPage parsePageMarkdown(String markdown) {
        WikiPage page = new WikiPage();
        // 解析 YAML frontmatter
        if (markdown.startsWith("---")) {
            int endIdx = markdown.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = markdown.substring(3, endIdx).trim();
                String body = markdown.substring(endIdx + 3).trim();

                for (String line : frontmatter.split("\n")) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx > 0) {
                        String key = line.substring(0, colonIdx).trim();
                        String value = line.substring(colonIdx + 1).trim();
                        switch (key) {
                            case "title" -> page.setTitle(value);
                            case "page_type" -> page.setPageType(value);
                            case "category" -> page.setCategory(value);
                            case "software" -> page.setSoftware(value);
                            case "version" -> page.setVersion(value);
                            case "summary" -> page.setSummary(value);
                            case "status" -> page.setStatus(value);
                            case "compiled_by" -> page.setCompiledBy(value);
                        }
                    }
                }
                page.setContent(body);
            }
        } else {
            page.setContent(markdown);
        }

        if (page.getTitle() == null || page.getPageType() == null) {
            log.warn("Skipping page with missing title or page_type");
            return null;
        }
        return page;
    }

    private int importLinks(String linksJson, Map<String, WikiPage> importedPages) {
        int created = 0;
        try {
            JsonArray links = gson.fromJson(linksJson, JsonArray.class);
            for (JsonElement elem : links) {
                JsonObject linkObj = elem.getAsJsonObject();
                String fromTitle = linkObj.get("from").getAsString();
                String toTitle = linkObj.get("to").getAsString();

                WikiPage fromPage = importedPages.get(fromTitle);
                WikiPage toPage = importedPages.get(toTitle);

                if (fromPage != null && toPage != null && fromPage.getId() != null && toPage.getId() != null) {
                    WikiLink link = new WikiLink();
                    link.setFromPageId(fromPage.getId());
                    link.setToPageId(toPage.getId());
                    link.setLinkType(linkObj.has("type") ? linkObj.get("type").getAsString() : "REFERENCES");
                    if (linkObj.has("confidence")) {
                        link.setConfidence(new BigDecimal(linkObj.get("confidence").getAsString()));
                    }
                    linkRepo.save(link);
                    created++;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to import some links: {}", e.getMessage());
        }
        return created;
    }

    private String readEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
