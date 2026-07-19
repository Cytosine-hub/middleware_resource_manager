package com.middleware.manager.module.security;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.spi.AbstractRoleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 网络安全岗位模块壳（编码独立）。对外展示「网络安全」，数据范围沿用既有「安全」分类（见 PortalRole）。
 * 接入点默认门户后端，可用 {@code app.modules.security.api-base} 单独配置。
 */
@Component
public class SecurityRoleModule extends AbstractRoleModule {

    public SecurityRoleModule(@Value("${app.modules.security.api-base:/api}") String apiBase) {
        super(PortalRole.SECURITY, apiBase);
    }
}
