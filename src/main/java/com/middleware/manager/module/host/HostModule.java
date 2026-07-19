package com.middleware.manager.module.host;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.spi.AbstractRoleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 主机岗位模块壳（编码独立）。接入点默认门户后端，可用 {@code app.modules.host.api-base} 单独配置。
 */
@Component
public class HostModule extends AbstractRoleModule {

    public HostModule(@Value("${app.modules.host.api-base:/api}") String apiBase) {
        super(PortalRole.HOST, apiBase);
    }
}
