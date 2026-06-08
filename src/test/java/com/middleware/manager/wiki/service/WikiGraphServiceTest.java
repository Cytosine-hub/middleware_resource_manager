package com.middleware.manager.wiki.service;

import com.middleware.manager.wiki.entity.WikiLink;
import com.middleware.manager.wiki.entity.WikiPage;
import com.middleware.manager.wiki.repository.WikiLinkMapper;
import com.middleware.manager.wiki.repository.WikiPageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class WikiGraphServiceTest {

    @Mock
    private WikiPageMapper pageMapper;
    @Mock
    private WikiLinkMapper linkMapper;
    @Mock
    private WikiPermissionService permissionService;

    private WikiGraphService graphService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        graphService = new WikiGraphService(pageMapper, linkMapper, permissionService);
        ReflectionTestUtils.setField(graphService, "maxNodes", 1000);
    }

    @Test
    @DisplayName("登录用户图谱包含有权限的草稿页面")
    void authenticatedGraphIncludesVisibleDraftPages() {
        WikiPage entity = page(1L, "TongWeb", "ENTITY", "DRAFT");
        WikiPage concept = page(2L, "集群管理", "CONCEPT", "DRAFT");
        List<WikiPage> pages = List.of(entity, concept);
        TestingAuthenticationToken authentication = authentication();

        when(pageMapper.findAll()).thenReturn(pages);
        when(permissionService.filterVisiblePages(authentication, pages)).thenReturn(pages);
        when(linkMapper.findAll()).thenReturn(List.of(link(1L, 2L)));

        Map<String, Object> graph = graphService.buildGraph(authentication);

        assertEquals(2, ((List<?>) graph.get("nodes")).size());
        assertEquals(1, ((List<?>) graph.get("links")).size());
    }

    @Test
    @DisplayName("匿名图谱不展示草稿页面")
    void publicGraphExcludesDraftPages() {
        when(pageMapper.findAll()).thenReturn(List.of(page(1L, "TongWeb", "ENTITY", "DRAFT")));
        when(linkMapper.findAll()).thenReturn(List.of());

        Map<String, Object> graph = graphService.buildGraph(null);

        assertEquals(0, ((List<?>) graph.get("nodes")).size());
        assertEquals(0, ((List<?>) graph.get("links")).size());
    }

    private TestingAuthenticationToken authentication() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("mwadmin", "n/a", "ROLE_MIDDLEWARE_ADMIN");
        authentication.setAuthenticated(true);
        return authentication;
    }

    private WikiPage page(Long id, String title, String pageType, String status) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setTitle(title);
        page.setPageType(pageType);
        page.setStatus(status);
        page.setCategory("中间件");
        page.setSoftware("TongWeb V7.0");
        page.setSourceRefs("doc.pdf");
        return page;
    }

    private WikiLink link(Long from, Long to) {
        WikiLink link = new WikiLink();
        link.setFromPageId(from);
        link.setToPageId(to);
        link.setLinkType("RELATED");
        link.setConfidence(BigDecimal.ONE);
        return link;
    }
}
