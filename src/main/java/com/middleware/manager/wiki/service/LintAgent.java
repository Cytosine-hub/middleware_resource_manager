package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.LintResult;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.LintResultMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LintAgent {

    private static final Logger log = LoggerFactory.getLogger(LintAgent.class);
    /** @see LinkResolver#WIKILINK_PATTERN */
    private static final Pattern WIKILINK_PATTERN = LinkResolver.WIKILINK_PATTERN;

    private final WikiPageMapper pageMapper;
    private final LintResultMapper lintResultMapper;

    public LintAgent(WikiPageMapper pageMapper,
                     LintResultMapper lintResultMapper) {
        this.pageMapper = pageMapper;
        this.lintResultMapper = lintResultMapper;
    }

    /**
     * Run all lint checks, persist results, and return them.
     */
    @Transactional
    public List<LintResult> runLint() {
        // Clean up old resolved results before running
        lintResultMapper.deleteResolved();

        List<LintResult> results = new ArrayList<>();
        results.addAll(detectOrphanPages());
        results.addAll(detectBrokenLinks());
        results.addAll(detectStaleContent());
        results.addAll(detectUnresolvedContradictions());

        // Persist all new results
        for (LintResult result : results) {
            lintResultMapper.insert(result);
        }

        log.info("Lint run completed: {} issues found", results.size());
        return results;
    }

    /**
     * Find ACTIVE pages with no incoming links (orphan pages).
     */
    public List<LintResult> detectOrphanPages() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> orphans = pageMapper.findOrphanPages();
        for (WikiPage page : orphans) {
            LintResult r = new LintResult();
            r.setLintType("ORPHAN");
            r.setPageId(page.getId());
            r.setDescription("Page \"" + page.getTitle() + "\" has no incoming links and may be hard to discover.");
            r.setSeverity("MEDIUM");
            r.setResolved(false);
            r.setCreatedAt(LocalDateTime.now());
            results.add(r);
        }
        log.debug("Detected {} orphan pages", orphans.size());
        return results;
    }

    /**
     * Extract [[wikilinks]] from all pages and check if targets exist.
     */
    public List<LintResult> detectBrokenLinks() {
        List<LintResult> results = new ArrayList<>();

        // Build a set of all existing page titles
        Set<String> existingTitles = new HashSet<>();
        for (WikiPage p : pageMapper.findAll()) {
            existingTitles.add(p.getTitle());
        }

        // Scan all pages for [[links]]
        List<WikiPage> allPages = pageMapper.findAll();
        for (WikiPage page : allPages) {
            if (page.getContent() == null) continue;
            Matcher matcher = WIKILINK_PATTERN.matcher(page.getContent());
            while (matcher.find()) {
                String target = matcher.group(1).trim();
                if (!existingTitles.contains(target)) {
                    LintResult r = new LintResult();
                    r.setLintType("BROKEN_LINK");
                    r.setPageId(page.getId());
                    r.setDescription("Page \"" + page.getTitle() + "\" contains broken link [[" + target + "]] — target page does not exist.");
                    r.setSeverity("HIGH");
                    r.setResolved(false);
                    r.setCreatedAt(LocalDateTime.now());
                    results.add(r);
                }
            }
        }
        log.debug("Detected {} broken links", results.size());
        return results;
    }

    /**
     * Find ACTIVE pages that haven't been updated in over 365 days.
     */
    public List<LintResult> detectStaleContent() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> stalePages = pageMapper.findStalePages(365);
        for (WikiPage page : stalePages) {
            LintResult r = new LintResult();
            r.setLintType("STALE");
            r.setPageId(page.getId());
            r.setDescription("Page \"" + page.getTitle() + "\" has not been updated for over 365 days (last updated: " + page.getUpdatedAt() + ").");
            r.setSeverity("LOW");
            r.setResolved(false);
            r.setCreatedAt(LocalDateTime.now());
            results.add(r);
        }
        log.debug("Detected {} stale pages", stalePages.size());
        return results;
    }

    /**
     * Find pages with status=CONTRADICTED.
     */
    public List<LintResult> detectUnresolvedContradictions() {
        List<LintResult> results = new ArrayList<>();
        List<WikiPage> contradicted = pageMapper.findByStatus("CONTRADICTED");
        for (WikiPage page : contradicted) {
            LintResult r = new LintResult();
            r.setLintType("CONTRADICTION");
            r.setPageId(page.getId());
            r.setDescription("Page \"" + page.getTitle() + "\" is marked as CONTRADICTED. " +
                    (page.getContradictionNote() != null ? "Note: " + page.getContradictionNote() : "No details provided."));
            r.setSeverity("HIGH");
            r.setResolved(false);
            r.setCreatedAt(LocalDateTime.now());
            results.add(r);
        }
        log.debug("Detected {} contradicted pages", contradicted.size());
        return results;
    }
}
