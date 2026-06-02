package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkRepository;
import com.middleware.manager.wiki.repository.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Wiki 导出服务：将 Wiki 页面导出为可传输的 ZIP 包。
 * 用于外网编译 → 内网导入的数据交换。
 */
@Service
public class WikiExportService {

    private static final Logger log = LoggerFactory.getLogger(WikiExportService.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final WikiPageRepository pageRepo;
    private final WikiLinkRepository linkRepo;

    public WikiExportService(WikiPageRepository pageRepo, WikiLinkRepository linkRepo) {
        this.pageRepo = pageRepo;
        this.linkRepo = linkRepo;
    }

    /**
     * 导出所有 ACTIVE 状态的 Wiki 页面为 ZIP 包。
     */
    public byte[] exportAll() throws IOException {
        List<WikiPage> pages = pageRepo.findByStatus("ACTIVE");
        return exportPages(pages);
    }

    /**
     * 按分类导出 Wiki 页面。
     */
    public byte[] exportByCategory(String category) throws IOException {
        List<WikiPage> pages = pageRepo.findByCategory(category);
        return exportPages(pages);
    }

    private byte[] exportPages(List<WikiPage> pages) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // manifest.json
            JsonObject manifest = buildManifest(pages);
            addZipEntry(zos, "manifest.json", gson.toJson(manifest));

            // pages/*.md
            for (WikiPage page : pages) {
                String fileName = sanitizeFileName(page.getTitle()) + "_" + page.getPageType() + ".md";
                String content = buildPageMarkdown(page);
                addZipEntry(zos, "pages/" + fileName, content);
            }

            // links.json
            JsonArray linksArray = new JsonArray();
            for (WikiPage page : pages) {
                List<WikiLink> links = linkRepo.findByFromPageId(page.getId());
                for (WikiLink link : links) {
                    // 只导出页面都在导出范围内的链接
                    if (pages.stream().anyMatch(p -> p.getId().equals(link.getToPageId()))) {
                        JsonObject linkObj = new JsonObject();
                        linkObj.addProperty("from", page.getTitle());
                        WikiPage toPage = pages.stream().filter(p -> p.getId().equals(link.getToPageId())).findFirst().orElse(null);
                        linkObj.addProperty("to", toPage != null ? toPage.getTitle() : "unknown");
                        linkObj.addProperty("type", link.getLinkType());
                        if (link.getConfidence() != null) linkObj.addProperty("confidence", link.getConfidence());
                        linksArray.add(linkObj);
                    }
                }
            }
            addZipEntry(zos, "links.json", gson.toJson(linksArray));
        }
        log.info("Exported {} pages, ZIP size: {} bytes", pages.size(), baos.size());
        return baos.toByteArray();
    }

    private JsonObject buildManifest(List<WikiPage> pages) {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("version", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        manifest.addProperty("exported_at", LocalDateTime.now().toString());
        manifest.addProperty("total_pages", pages.size());

        Map<String, Integer> categoryCount = new HashMap<>();
        for (WikiPage page : pages) {
            String cat = page.getCategory() != null ? page.getCategory() : "未分类";
            categoryCount.merge(cat, 1, Integer::sum);
        }
        JsonObject categories = new JsonObject();
        categoryCount.forEach(categories::addProperty);
        manifest.add("categories", categories);

        return manifest;
    }

    private String buildPageMarkdown(WikiPage page) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(page.getTitle()).append("\n");
        sb.append("page_type: ").append(page.getPageType()).append("\n");
        if (page.getCategory() != null) sb.append("category: ").append(page.getCategory()).append("\n");
        if (page.getSoftware() != null) sb.append("software: ").append(page.getSoftware()).append("\n");
        if (page.getVersion() != null) sb.append("version: ").append(page.getVersion()).append("\n");
        if (page.getSummary() != null) sb.append("summary: ").append(page.getSummary()).append("\n");
        sb.append("status: ").append(page.getStatus()).append("\n");
        if (page.getCompiledBy() != null) sb.append("compiled_by: ").append(page.getCompiledBy()).append("\n");
        sb.append("---\n\n");
        sb.append(page.getContent());
        return sb.toString();
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]", "_");
    }
}
