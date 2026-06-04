package com.middleware.manager.wiki.service;

import com.middleware.manager.knowledge.embedding.EmbeddingService;
import com.middleware.manager.knowledge.store.VectorStore;
import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class WikiSearchService {

    private static final Logger log = LoggerFactory.getLogger(WikiSearchService.class);
    private static final float VECTOR_SCORE_THRESHOLD = 0.5f;

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private EmbeddingService embeddingService;

    @Value("${app.wiki.search.max-context-pages:8}")
    private int maxContextPages;

    @Value("${app.wiki.search.max-content-chars:2000}")
    private int maxContentChars;

    @Value("${app.wiki.search.graph-hop-limit:1}")
    private int graphHopLimit;

    @Value("${app.wiki.search.fulltext-min-results:3}")
    private int fulltextMinResults;

    public WikiSearchService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    public static class WikiSearchResult {
        private WikiPage page;
        private float score;
        private String matchSource;  // "fulltext", "vector", "graph"
        private List<String> relatedPageTitles;

        public WikiPage getPage() { return page; }
        public void setPage(WikiPage page) { this.page = page; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public String getMatchSource() { return matchSource; }
        public void setMatchSource(String matchSource) { this.matchSource = matchSource; }
        public List<String> getRelatedPageTitles() { return relatedPageTitles; }
        public void setRelatedPageTitles(List<String> relatedPageTitles) { this.relatedPageTitles = relatedPageTitles; }
    }

    public List<WikiSearchResult> search(String query, int topK) {
        // Early exit if no active pages
        int activeCount = pageMapper.countByStatus("ACTIVE");
        if (activeCount == 0) {
            return Collections.emptyList();
        }

        // Stage 1: MySQL FULLTEXT
        List<WikiPage> fulltextHits = pageMapper.fulltextSearch(query, topK * 2);

        // Stage 2: Vector fallback (if fulltext results insufficient)
        if (fulltextHits.size() < fulltextMinResults && vectorStore != null && embeddingService != null) {
            try {
                float[] queryVec = embeddingService.embed(query);
                List<VectorStore.VectorSearchResult> vecResults = vectorStore.search(queryVec, topK * 2);
                for (VectorStore.VectorSearchResult vr : vecResults) {
                    if (vr.getScore() < VECTOR_SCORE_THRESHOLD) break;
                    Map<String, String> meta = vr.getMetadata();
                    if (meta != null && "wiki".equals(meta.get("source"))) {
                        String pageIdStr = meta.get("pageId");
                        if (pageIdStr != null) {
                            Long pageId = Long.parseLong(pageIdStr);
                            boolean exists = fulltextHits.stream().anyMatch(p -> p.getId().equals(pageId));
                            if (!exists) {
                                WikiPage page = pageMapper.findById(pageId);
                                if (page != null) {
                                    fulltextHits.add(page);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Vector search for Wiki failed, continuing with fulltext only: {}", e.getMessage());
            }
        }

        // Stage 3: Graph expansion
        Set<Long> directHitIds = new LinkedHashSet<>();
        for (WikiPage p : fulltextHits) {
            directHitIds.add(p.getId());
        }

        Set<Long> expandedIds = new LinkedHashSet<>(directHitIds);
        for (WikiPage page : fulltextHits) {
            List<WikiLink> links = linkMapper.findAllByPageId(page.getId());
            for (WikiLink link : links) {
                Long relatedId = link.getFromPageId().equals(page.getId())
                        ? link.getToPageId() : link.getFromPageId();
                expandedIds.add(relatedId);
            }
        }

        // Stage 4: Fetch, dedup, rank, limit
        List<WikiPage> allPages = pageMapper.findByIds(new ArrayList<>(expandedIds));
        Map<Long, WikiPage> pageMap = new LinkedHashMap<>();
        for (WikiPage p : allPages) {
            pageMap.put(p.getId(), p);
        }

        List<WikiSearchResult> results = new ArrayList<>();
        Set<Long> added = new LinkedHashSet<>();

        // Direct hits first
        for (WikiPage directHit : fulltextHits) {
            WikiPage full = pageMap.get(directHit.getId());
            if (full != null && !added.contains(full.getId())) {
                WikiSearchResult sr = new WikiSearchResult();
                sr.setPage(full);
                sr.setScore(1.0f);
                sr.setMatchSource("fulltext");
                results.add(sr);
                added.add(full.getId());
            }
        }

        // Graph-expanded pages
        for (Long expandedId : expandedIds) {
            if (!added.contains(expandedId) && pageMap.containsKey(expandedId)) {
                WikiPage page = pageMap.get(expandedId);
                WikiSearchResult sr = new WikiSearchResult();
                sr.setPage(page);
                sr.setScore(0.6f);
                sr.setMatchSource("graph");
                results.add(sr);
                added.add(expandedId);
            }
        }

        // Enrich direct hits with related page titles
        for (WikiSearchResult sr : results) {
            if ("fulltext".equals(sr.getMatchSource())) {
                List<WikiLink> links = linkMapper.findAllByPageId(sr.getPage().getId());
                List<String> relatedTitles = new ArrayList<>();
                for (WikiLink link : links) {
                    Long relatedId = link.getFromPageId().equals(sr.getPage().getId())
                            ? link.getToPageId() : link.getFromPageId();
                    WikiPage related = pageMap.get(relatedId);
                    if (related != null) {
                        relatedTitles.add(related.getTitle());
                    }
                }
                sr.setRelatedPageTitles(relatedTitles);
            }
        }

        // Cap to maxContextPages
        if (results.size() > maxContextPages) {
            results = results.subList(0, maxContextPages);
        }

        return results;
    }
}
