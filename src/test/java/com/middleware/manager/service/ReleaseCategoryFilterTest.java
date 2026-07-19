package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.ReleaseAssetRepository;
import com.middleware.manager.repository.SoftwareTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

/**
 * TC-03 / TC-04：软件下载公共模块按岗位（category）筛选及边界处理。
 * 岗位分类通过 releaseAsset -> softwareType.category 关联，用真实 H2 校验 JPA join 过滤。
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReleaseCategoryFilterTest {

    @Autowired
    private ReleaseAssetRepository releaseRepository;

    @Autowired
    private SoftwareTypeRepository softwareTypeRepository;

    private ReleaseService releaseService;

    @BeforeEach
    void setUp() {
        // category 过滤只依赖 repository，StorageService/SoftwareTypeService 用 mock 即可
        releaseService = new ReleaseService(releaseRepository, mock(StorageService.class), mock(SoftwareTypeService.class));
    }

    private SoftwareType softwareType(String category, String name) {
        SoftwareType type = new SoftwareType();
        type.setCategory(category);
        type.setName(name);
        type.setActive(true);
        return softwareTypeRepository.save(type);
    }

    private void publishedRelease(String name, SoftwareType type) {
        ReleaseAsset asset = new ReleaseAsset();
        asset.setMiddlewareName(name);
        asset.setSoftwareType(type);
        asset.setVersion("1.0");
        asset.setPublished(true);
        asset.setOriginalFileName(name + ".tar.gz");
        asset.setStoredFileName(name + "-stored.tar.gz");
        asset.setFileSize(1024L);
        asset.setDownloadCount(0L);
        releaseRepository.save(asset);
    }

    @Test
    void TC_03_软件下载按岗位筛选仅返回对应岗位资源() {
        SoftwareType mw = softwareType("中间件", "Nginx");
        SoftwareType db = softwareType("数据库", "MySQL");
        publishedRelease("Nginx", mw);
        publishedRelease("Tomcat", mw);
        publishedRelease("MySQL", db);

        Page<ReleaseAsset> middleware = releaseService.listPublishedReleases("", "", "中间件", 0, 12);
        assertThat(middleware.getTotalElements()).isEqualTo(2);
        assertThat(middleware.getContent())
                .allMatch(r -> "中间件".equals(r.getSoftwareType().getCategory()));

        // 全部岗位（category 为空）返回全部
        assertThat(releaseService.listPublishedReleases("", "", "", 0, 12).getTotalElements()).isEqualTo(3);
    }

    @Test
    void TC_04_软件下载无数据与单条数据岗位的边界处理() {
        SoftwareType mw = softwareType("中间件", "Nginx");
        SoftwareType net = softwareType("网络", "F5");
        publishedRelease("Nginx", mw);
        publishedRelease("F5", net);

        // 无内容岗位：空结果、不报错
        assertThat(releaseService.listPublishedReleases("", "", "主机", 0, 12).getTotalElements()).isZero();
        // 单条内容岗位：正常返回
        assertThat(releaseService.listPublishedReleases("", "", "网络", 0, 12).getTotalElements()).isEqualTo(1);
    }
}
