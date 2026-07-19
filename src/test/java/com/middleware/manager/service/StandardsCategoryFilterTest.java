package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.repository.ParameterStandardRepository;
import com.middleware.manager.repository.ReviewRecordRepository;
import com.middleware.manager.repository.StandardDocumentRepository;
import com.middleware.manager.repository.StandardParameterRepository;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TC-03 / TC-04：标准发布公共模块按岗位（category）筛选及边界数据处理。
 * 参数标准与标准文档两条公开列表都新增了 category 过滤，纯逻辑用 Mockito 验证。
 */
class StandardsCategoryFilterTest {

    private ParameterStandard param(String category) {
        ParameterStandard ps = new ParameterStandard();
        ps.setStatus("PUBLISHED");
        ps.setCategory(category);
        return ps;
    }

    private StandardDocument doc(String category) {
        StandardDocument d = new StandardDocument();
        d.setStatus("PUBLISHED");
        d.setCategory(category);
        return d;
    }

    private ParameterStandardService parameterService(List<ParameterStandard> data) {
        ParameterStandardRepository repo = mock(ParameterStandardRepository.class);
        when(repo.findByStatusInOrderByPublishedAtDesc(Arrays.asList("PUBLISHED", "MODIFYING"))).thenReturn(data);
        return new ParameterStandardService(repo,
                mock(StandardParameterRepository.class),
                mock(ReviewRecordRepository.class),
                mock(SoftwareTypeService.class));
    }

    private StandardDocumentService documentService(List<StandardDocument> data) {
        StandardDocumentRepository repo = mock(StandardDocumentRepository.class);
        when(repo.findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc("STANDARD", "PUBLISHED")).thenReturn(data);
        return new StandardDocumentService(repo,
                mock(ReviewRecordRepository.class),
                mock(StandardParameterService.class),
                mock(SoftwareTypeService.class),
                mock(ParameterStandardRepository.class));
    }

    @Test
    void TC_03_参数标准按岗位筛选仅返回对应岗位内容() {
        ParameterStandardService service = parameterService(
                Arrays.asList(param("中间件"), param("数据库"), param("中间件")));

        assertThat(service.listPublicStandards("中间件")).hasSize(2);
        assertThat(service.listPublicStandards("数据库")).hasSize(1);
        // 未指定岗位（全部）返回全部
        assertThat(service.listPublicStandards("")).hasSize(3);
        assertThat(service.listPublicStandards(null)).hasSize(3);
    }

    @Test
    void TC_04_参数标准无数据或单条数据的边界处理() {
        ParameterStandardService service = parameterService(
                Arrays.asList(param("中间件"), param("网络")));

        // 无内容的岗位：返回空列表而非报错
        assertThat(service.listPublicStandards("主机")).isEmpty();
        // 仅有一条内容的岗位：正常返回单条
        assertThat(service.listPublicStandards("网络")).hasSize(1);
    }

    @Test
    void TC_03_标准文档按岗位筛选仅返回对应岗位内容() {
        StandardDocumentService service = documentService(
                Arrays.asList(doc("主机"), doc("安全"), doc("主机")));

        assertThat(service.listPublishedStandards("主机")).hasSize(2);
        assertThat(service.listPublishedStandards("安全")).hasSize(1);
        assertThat(service.listPublishedStandards("")).hasSize(3);
    }

    @Test
    void TC_04_标准文档无数据岗位返回友好空结果() {
        StandardDocumentService service = documentService(Arrays.asList(doc("中间件")));
        assertThat(service.listPublishedStandards("网络")).isEmpty();
        assertThat(service.listPublishedStandards("中间件")).hasSize(1);
    }
}
