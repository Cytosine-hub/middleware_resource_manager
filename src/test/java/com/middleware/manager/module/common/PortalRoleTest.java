package com.middleware.manager.module.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TC-01 / TC-02 / TC-06：岗位专属区域五大岗位入口与独立岗位模块的统一契约。
 * 首页岗位入口、公共模块左侧岗位导航都基于 {@link PortalRole}，保证顺序、标识、名称一致。
 */
class PortalRoleTest {

    @Test
    void TC_01_岗位专属区域固定为五大岗位且顺序稳定() {
        List<PortalRole> ordered = PortalRole.ordered();
        assertThat(ordered).hasSize(5);
        assertThat(ordered).containsExactly(
                PortalRole.MIDDLEWARE,
                PortalRole.DATABASE,
                PortalRole.HOST,
                PortalRole.NETWORK,
                PortalRole.SECURITY);
    }

    @Test
    void TC_02_五大岗位入口具备可路由的岗位标识与展示名() {
        assertThat(PortalRole.ordered().stream().map(PortalRole::getId).toList())
                .containsExactly("middleware", "database", "host", "network", "security");
        assertThat(PortalRole.ordered().stream().map(PortalRole::getLabel).toList())
                .containsExactly("中间件", "数据库", "主机", "网络", "网络安全");
        assertThat(PortalRole.fromId("middleware")).isEqualTo(PortalRole.MIDDLEWARE);
        assertThatThrownBy(() -> PortalRole.fromId("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void TC_06_岗位数据范围与既有分类对齐_网络安全映射到安全() {
        assertThat(PortalRole.categories())
                .containsExactly("中间件", "数据库", "主机", "网络", "安全");
        // 网络安全岗对外名称为「网络安全」，数据范围沿用系统既有「安全」分类
        assertThat(PortalRole.SECURITY.getLabel()).isEqualTo("网络安全");
        assertThat(PortalRole.SECURITY.getCategory()).isEqualTo("安全");
        assertThat(PortalRole.isRoleCategory("中间件")).isTrue();
        assertThat(PortalRole.isRoleCategory("不存在")).isFalse();
    }
}
