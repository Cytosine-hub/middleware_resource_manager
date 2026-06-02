package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkRepository;
import com.middleware.manager.wiki.repository.WikiPageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 Wiki 页面中的 [[页面名]] 交叉引用，建立 wiki_links 关系。
 */
@Service
public class LinkResolver {

    private static final Logger log = LoggerFactory.getLogger(LinkResolver.class);
    private static final Pattern WIKILINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final WikiPageRepository pageRepo;
    private final WikiLinkRepository linkRepo;

    public LinkResolver(WikiPageRepository pageRepo, WikiLinkRepository linkRepo) {
        this.pageRepo = pageRepo;
        this.linkRepo = linkRepo;
    }

    /**
     * 从页面内容中提取所有 [[页面名]] 引用。
     */
    public List<String> extractWikiLinks(String content) {
        List<String> links = new ArrayList<>();
        if (content == null) return links;
        Matcher matcher = WIKILINK_PATTERN.matcher(content);
        while (matcher.find()) {
            links.add(matcher.group(1).trim());
        }
        return links;
    }

    /**
     * 为一批 Wiki 页面解析交叉引用并建立 wiki_links。
     * 返回新创建的链接数。
     */
    public int resolveLinks(List<WikiPage> pages) {
        // 构建标题到页面ID的索引
        Map<String, Long> titleIndex = new HashMap<>();
        for (WikiPage page : pages) {
            if (page.getId() != null) {
                titleIndex.put(page.getTitle(), page.getId());
            }
        }
        // 也加载已有页面的索引
        List<WikiPage> existingPages = pageRepo.findAll();
        for (WikiPage page : existingPages) {
            titleIndex.putIfAbsent(page.getTitle(), page.getId());
        }

        int created = 0;
        for (WikiPage page : pages) {
            if (page.getId() == null) continue;
            List<String> linkTargets = extractWikiLinks(page.getContent());
            for (String target : linkTargets) {
                Long targetId = titleIndex.get(target);
                if (targetId == null) {
                    log.debug("Wiki link target not found: [[{}]] in page '{}'", target, page.getTitle());
                    continue;
                }
                if (targetId.equals(page.getId())) continue; // 不链接自己

                WikiLink link = new WikiLink();
                link.setFromPageId(page.getId());
                link.setToPageId(targetId);
                link.setLinkType("REFERENCES");
                link.setConfidence(new BigDecimal("0.90"));
                link = linkRepo.save(link);
                if (link.getId() != null) {
                    created++;
                }
            }
        }
        log.info("Resolved {} wiki links from {} pages", created, pages.size());
        return created;
    }

    /**
     * 检查页面中的断链（引用了不存在的页面）。
     */
    public List<String> findBrokenLinks(WikiPage page) {
        List<String> broken = new ArrayList<>();
        List<String> targets = extractWikiLinks(page.getContent());
        Set<String> existingTitles = new HashSet<>();
        for (WikiPage p : pageRepo.findAll()) {
            existingTitles.add(p.getTitle());
        }
        for (String target : targets) {
            if (!existingTitles.contains(target)) {
                broken.add(target);
            }
        }
        return broken;
    }
}
