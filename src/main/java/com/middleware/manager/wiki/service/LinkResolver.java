package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LinkResolver {

    private static final Logger log = LoggerFactory.getLogger(LinkResolver.class);
    public static final Pattern WIKILINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    public LinkResolver(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    public List<String> extractWikiLinks(String content) {
        List<String> links = new ArrayList<>();
        if (content == null) return links;
        Matcher matcher = WIKILINK_PATTERN.matcher(content);
        while (matcher.find()) {
            links.add(matcher.group(1).trim());
        }
        return links;
    }

    public int resolveLinks(List<WikiPage> pages) {
        Map<String, Long> titleIndex = new HashMap<>();
        for (WikiPage page : pages) {
            if (page.getId() != null) {
                titleIndex.put(page.getTitle(), page.getId());
            }
        }
        List<WikiPage> existingPages = pageMapper.findAll();
        for (WikiPage page : existingPages) {
            titleIndex.putIfAbsent(page.getTitle(), page.getId());
        }

        int created = 0;
        for (WikiPage page : pages) {
            if (page.getId() == null) continue;
            List<String> linkTargets = extractWikiLinks(page.getContent());
            for (String target : linkTargets) {
                Long targetId = titleIndex.get(target);
                if (targetId == null || targetId.equals(page.getId())) continue;

                WikiLink link = new WikiLink();
                link.setFromPageId(page.getId());
                link.setToPageId(targetId);
                link.setLinkType("REFERENCES");
                link.setConfidence(new BigDecimal("0.90"));
                created += linkMapper.insertIgnore(link);
            }
        }
        log.info("Resolved {} wiki links from {} pages", created, pages.size());
        return created;
    }

    public List<String> findBrokenLinks(WikiPage page) {
        List<String> broken = new ArrayList<>();
        List<String> targets = extractWikiLinks(page.getContent());
        Set<String> existingTitles = new HashSet<>();
        for (WikiPage p : pageMapper.findAll()) {
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
