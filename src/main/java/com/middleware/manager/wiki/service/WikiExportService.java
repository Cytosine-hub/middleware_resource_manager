package com.middleware.manager.wiki.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiExportService {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    public WikiExportService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    public byte[] exportAll() throws IOException {
        List<WikiPage> pages = pageMapper.findAll();
        return exportPages(pages);
    }

    public byte[] exportByCategory(String category) throws IOException {
        List<WikiPage> pages = pageMapper.findByCategory(category);
        return exportPages(pages);
    }

    private byte[] exportPages(List<WikiPage> pages) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            JsonObject manifest = buildManifest(pages);
            addZipEntry(zos, "manifest.json", gson.toJson(manifest));

            for (WikiPage page : pages) {
                String fileName = sanitizeFileName(page.getTitle()) + "_" + page.getPageType() + ".md";
                String content = buildPageMarkdown(page);
                addZipEntry(zos, "pages/" + fileName, content);
            }

            // 构建页面 ID → title 索引，用于批量解析链接
            Map<Long, String> pageTitleMap = new HashMap<>();
            for (WikiPage page : pages) {
                pageTitleMap.put(page.getId(), page.getTitle());
            }

            JsonArray linksArray = new JsonArray();
            for (WikiPage page : pages) {
                List<WikiLink> links = linkMapper.findByFromPageId(page.getId());
                for (WikiLink link : links) {
                    String toTitle = pageTitleMap.get(link.getToPageId());
                    if (toTitle != null) {
                        JsonObject linkObj = new JsonObject();
                        linkObj.addProperty("from", page.getTitle());
                        linkObj.addProperty("to", toTitle);
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
