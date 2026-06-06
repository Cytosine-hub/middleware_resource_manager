package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WikiGraphService {

    private final WikiPageMapper pageMapper;
    private final WikiLinkMapper linkMapper;

    // 5-signal weights
    private static final double W_DIRECT_LINK = 3.0;
    private static final double W_SAME_SOFTWARE = 4.0;
    private static final double W_SAME_CATEGORY = 2.0;
    private static final double W_CO_OCCURRENCE = 1.5;
    private static final double W_TRANSITIVE_DEP = 2.5;

    public WikiGraphService(WikiPageMapper pageMapper, WikiLinkMapper linkMapper) {
        this.pageMapper = pageMapper;
        this.linkMapper = linkMapper;
    }

    /**
     * 构建完整的知识图谱：5信号评分 + Louvain 社区检测
     */
    public Map<String, Object> buildGraph() {
        List<WikiPage> pages = pageMapper.findAll();
        List<WikiLink> links = linkMapper.findAll();

        if (pages.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("nodes", Collections.emptyList());
            empty.put("links", Collections.emptyList());
            return empty;
        }

        // 构建数据索引
        Map<Long, WikiPage> pageMap = new HashMap<>();
        for (WikiPage p : pages) pageMap.put(p.getId(), p);

        Map<Long, Set<Long>> directLinks = new HashMap<>();
        Map<String, Set<Long>> softwareIndex = new HashMap<>();
        Map<String, Set<Long>> categoryIndex = new HashMap<>();
        Map<Long, Set<Long>> dependsOnChains = new HashMap<>();

        for (WikiPage p : pages) {
            directLinks.putIfAbsent(p.getId(), new HashSet<>());
            if (p.getSoftware() != null && !p.getSoftware().isEmpty()) {
                softwareIndex.computeIfAbsent(p.getSoftware(), k -> new HashSet<>()).add(p.getId());
            }
            if (p.getCategory() != null && !p.getCategory().isEmpty()) {
                categoryIndex.computeIfAbsent(p.getCategory(), k -> new HashSet<>()).add(p.getId());
            }
        }

        for (WikiLink link : links) {
            directLinks.computeIfAbsent(link.getFromPageId(), k -> new HashSet<>()).add(link.getToPageId());
            directLinks.computeIfAbsent(link.getToPageId(), k -> new HashSet<>()).add(link.getFromPageId());
            if ("DEPENDS_ON".equals(link.getLinkType())) {
                dependsOnChains.computeIfAbsent(link.getFromPageId(), k -> new HashSet<>()).add(link.getToPageId());
            }
        }

        // 5-signal 评分
        Graph<Long, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(DefaultWeightedEdge.class);
        for (WikiPage p : pages) graph.addVertex(p.getId());

        Map<String, Double> edgeWeights = new HashMap<>();
        Set<Long> allPageIds = pageMap.keySet();

        // 信号1: 直接链接
        for (var entry : directLinks.entrySet()) {
            long from = entry.getKey();
            for (long to : entry.getValue()) {
                if (from < to) addEdgeWeight(graph, edgeWeights, from, to, W_DIRECT_LINK);
            }
        }

        // 信号2: 同一软件
        for (Set<Long> group : softwareIndex.values()) {
            List<Long> list = new ArrayList<>(group);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    addEdgeWeight(graph, edgeWeights, list.get(i), list.get(j), W_SAME_SOFTWARE);
                }
            }
        }

        // 信号3: 同一分类（仅 OVERVIEW 页面之间，避免不同软件被拉到同一社区）
        for (Set<Long> group : categoryIndex.values()) {
            List<Long> list = new ArrayList<>(group);
            // 只取 OVERVIEW 类型的页面
            List<Long> overviews = list.stream()
                    .filter(id -> "OVERVIEW".equals(pageMap.get(id).getPageType()))
                    .collect(java.util.stream.Collectors.toList());
            for (int i = 0; i < overviews.size(); i++) {
                for (int j = i + 1; j < overviews.size(); j++) {
                    addEdgeWeight(graph, edgeWeights, overviews.get(i), overviews.get(j), W_SAME_CATEGORY);
                }
            }
            // OVERVIEW 到同分类其他页面也加边（分类入口连接）
            for (long ovId : overviews) {
                for (long pageId : list) {
                    if (ovId != pageId) {
                        addEdgeWeight(graph, edgeWeights, ovId, pageId, W_SAME_CATEGORY * 0.5);
                    }
                }
            }
        }

        // 信号5: 依赖链传递（1跳）
        for (var entry : dependsOnChains.entrySet()) {
            long from = entry.getKey();
            for (long mid : entry.getValue()) {
                Set<Long> transitive = dependsOnChains.getOrDefault(mid, Collections.emptySet());
                for (long to : transitive) {
                    if (from != to) addEdgeWeight(graph, edgeWeights, from, to, W_TRANSITIVE_DEP);
                }
            }
        }

        // 设置边权重
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            long s = graph.getEdgeSource(edge);
            long t = graph.getEdgeTarget(edge);
            String key = edgeKey(s, t);
            graph.setEdgeWeight(edge, edgeWeights.getOrDefault(key, 0.0));
        }

        // Louvain 社区检测
        Map<Long, Integer> communities = louvain(graph, allPageIds);

        // 社区名称（取每个社区中被引用最多的页面的软件/分类作为名称）
        Map<Integer, String> communityNames = computeCommunityNames(communities, pageMap);

        // 构建输出
        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Long, Integer> nodeIdMap = new HashMap<>();
        int idx = 0;
        for (WikiPage p : pages) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", idx);
            node.put("pageId", p.getId());
            node.put("name", p.getTitle());
            node.put("pageType", p.getPageType());
            node.put("category", p.getCategory());
            node.put("software", p.getSoftware());
            node.put("status", p.getStatus());
            node.put("community", communities.getOrDefault(p.getId(), 0));
            node.put("communityName", communityNames.getOrDefault(communities.getOrDefault(p.getId(), 0), ""));
            nodes.add(node);
            nodeIdMap.put(p.getId(), idx);
            idx++;
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (DefaultWeightedEdge edge : graph.edgeSet()) {
            long src = graph.getEdgeSource(edge);
            long tgt = graph.getEdgeTarget(edge);
            Integer from = nodeIdMap.get(src);
            Integer to = nodeIdMap.get(tgt);
            if (from != null && to != null) {
                double weight = graph.getEdgeWeight(edge);
                Map<String, Object> link = new HashMap<>();
                link.put("source", from);
                link.put("target", to);
                link.put("weight", Math.round(weight * 100.0) / 100.0);
                link.put("linkType", resolveLinkType(src, tgt, links));
                edges.add(link);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("links", edges);
        result.put("communityCount", communityNames.size());
        result.put("communities", communityNames);
        return result;
    }

    private void addEdgeWeight(Graph<Long, DefaultWeightedEdge> graph, Map<String, Double> weights,
                               long from, long to, double delta) {
        if (from == to) return;
        String key = edgeKey(from, to);
        weights.merge(key, delta, Double::sum);
        if (!graph.containsEdge(from, to)) {
            try {
                graph.addEdge(from, to);
            } catch (Exception e) {
                // edge already exists, ignore
            }
        }
    }

    private String edgeKey(long a, long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    private String resolveLinkType(long from, long to, List<WikiLink> links) {
        for (WikiLink l : links) {
            if ((l.getFromPageId().equals(from) && l.getToPageId().equals(to)) ||
                (l.getFromPageId().equals(to) && l.getToPageId().equals(from))) {
                return l.getLinkType();
            }
        }
        return "RELATED";
    }

    // ========== Louvain 社区检测 ==========

    private Map<Long, Integer> louvain(Graph<Long, DefaultWeightedEdge> graph, Set<Long> nodes) {
        if (nodes.size() <= 1) {
            Map<Long, Integer> result = new HashMap<>();
            int i = 0;
            for (long n : nodes) result.put(n, i++);
            return result;
        }

        // 初始化：每个节点一个社区
        Map<Long, Integer> nodeCommunity = new HashMap<>();
        int cid = 0;
        for (long n : nodes) nodeCommunity.put(n, cid++);

        double totalWeight = totalEdgeWeight(graph);
        if (totalWeight == 0) return nodeCommunity;

        // 迭代优化
        boolean improved = true;
        int maxIterations = 20;
        int iter = 0;
        while (improved && iter < maxIterations) {
            improved = false;
            iter++;

            // 随机顺序遍历节点
            List<Long> shuffled = new ArrayList<>(nodes);
            Collections.shuffle(shuffled);

            for (long node : shuffled) {
                int currentCommunity = nodeCommunity.get(node);
                double bestGain = 0;
                int bestCommunity = currentCommunity;

                // 计算邻居社区
                Set<Long> neighbors = getNeighbors(graph, node);
                Set<Integer> neighborCommunities = new HashSet<>();
                neighborCommunities.add(currentCommunity);
                for (long n : neighbors) {
                    neighborCommunities.add(nodeCommunity.get(n));
                }

                // 尝试移动到每个邻居社区
                for (int targetCommunity : neighborCommunities) {
                    if (targetCommunity == currentCommunity) continue;

                    double gain = modularityGain(graph, nodeCommunity, node, currentCommunity, targetCommunity, totalWeight);
                    if (gain > bestGain) {
                        bestGain = gain;
                        bestCommunity = targetCommunity;
                    }
                }

                if (bestCommunity != currentCommunity) {
                    nodeCommunity.put(node, bestCommunity);
                    improved = true;
                }
            }
        }

        // 重新编号社区（连续）
        return renumberCommunities(nodeCommunity);
    }

    private double modularityGain(Graph<Long, DefaultWeightedEdge> graph,
                                   Map<Long, Integer> nodeCommunity,
                                   long node, int fromCommunity, int toCommunity,
                                   double totalWeight) {
        double m2 = 2.0 * totalWeight;
        if (m2 == 0) return 0;

        double ki = weightedDegree(graph, node);
        double kiInFrom = weightToCommunity(graph, nodeCommunity, node, fromCommunity);
        double kiInTo = weightToCommunity(graph, nodeCommunity, node, toCommunity);
        double sigmaTotFrom = communityTotalDegree(graph, nodeCommunity, fromCommunity);
        double sigmaTotTo = communityTotalDegree(graph, nodeCommunity, toCommunity);

        // modularity gain from leaving fromCommunity
        double dqFrom = (kiInFrom / m2) - (sigmaTotFrom * ki / (m2 * m2));
        // modularity gain from joining toCommunity
        double dqTo = (kiInTo / m2) + (sigmaTotTo * ki / (m2 * m2)) - (ki * ki / (m2 * m2));

        return dqTo - dqFrom;
    }

    private double weightedDegree(Graph<Long, DefaultWeightedEdge> graph, long node) {
        double sum = 0;
        for (DefaultWeightedEdge e : graph.edgesOf(node)) {
            sum += graph.getEdgeWeight(e);
        }
        return sum;
    }

    private double weightToCommunity(Graph<Long, DefaultWeightedEdge> graph,
                                      Map<Long, Integer> nodeCommunity, long node, int community) {
        double sum = 0;
        for (DefaultWeightedEdge e : graph.edgesOf(node)) {
            long other = graph.getEdgeSource(e) == node ? graph.getEdgeTarget(e) : graph.getEdgeSource(e);
            if (nodeCommunity.getOrDefault(other, -1) == community && other != node) {
                sum += graph.getEdgeWeight(e);
            }
        }
        return sum;
    }

    private double communityTotalDegree(Graph<Long, DefaultWeightedEdge> graph,
                                         Map<Long, Integer> nodeCommunity, int community) {
        double sum = 0;
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            long src = graph.getEdgeSource(e);
            long tgt = graph.getEdgeTarget(e);
            boolean srcIn = nodeCommunity.getOrDefault(src, -1) == community;
            boolean tgtIn = nodeCommunity.getOrDefault(tgt, -1) == community;
            if (srcIn || tgtIn) {
                sum += graph.getEdgeWeight(e);
            }
        }
        return sum;
    }

    private Set<Long> getNeighbors(Graph<Long, DefaultWeightedEdge> graph, long node) {
        Set<Long> neighbors = new HashSet<>();
        for (DefaultWeightedEdge e : graph.edgesOf(node)) {
            long src = graph.getEdgeSource(e);
            long tgt = graph.getEdgeTarget(e);
            neighbors.add(src == node ? tgt : src);
        }
        return neighbors;
    }

    private Map<Long, Integer> renumberCommunities(Map<Long, Integer> original) {
        Map<Integer, Integer> remap = new HashMap<>();
        int[] counter = {0};
        Map<Long, Integer> result = new HashMap<>();
        for (long node : original.keySet()) {
            int oldComm = original.get(node);
            int newComm = remap.computeIfAbsent(oldComm, k -> counter[0]++);
            result.put(node, newComm);
        }
        return result;
    }

    private double totalEdgeWeight(Graph<Long, DefaultWeightedEdge> graph) {
        double sum = 0;
        for (DefaultWeightedEdge e : graph.edgeSet()) {
            sum += graph.getEdgeWeight(e);
        }
        return sum;
    }

    private Map<Integer, String> computeCommunityNames(Map<Long, Integer> communities, Map<Long, WikiPage> pageMap) {
        Map<Integer, Map<String, Integer>> communitySoftware = new HashMap<>();
        Map<Integer, Map<String, Integer>> communityCategory = new HashMap<>();

        for (var entry : communities.entrySet()) {
            WikiPage page = pageMap.get(entry.getKey());
            if (page == null) continue;
            int comm = entry.getValue();
            if (page.getSoftware() != null && !page.getSoftware().isEmpty()) {
                communitySoftware.computeIfAbsent(comm, k -> new HashMap<>())
                        .merge(page.getSoftware(), 1, Integer::sum);
            }
            if (page.getCategory() != null && !page.getCategory().isEmpty()) {
                communityCategory.computeIfAbsent(comm, k -> new HashMap<>())
                        .merge(page.getCategory(), 1, Integer::sum);
            }
        }

        Map<Integer, String> names = new HashMap<>();
        for (int comm : communitySoftware.keySet()) {
            String topSoftware = topKey(communitySoftware.get(comm));
            String topCategory = topKey(communityCategory.getOrDefault(comm, Collections.emptyMap()));
            if (topSoftware != null) {
                names.put(comm, topSoftware + (topCategory != null ? " (" + topCategory + ")" : ""));
            } else if (topCategory != null) {
                names.put(comm, topCategory);
            } else {
                names.put(comm, "社区 " + comm);
            }
        }
        return names;
    }

    private String topKey(Map<String, Integer> map) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
