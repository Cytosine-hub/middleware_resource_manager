package com.middleware.manager.module.network;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.spi.AbstractRoleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 网络岗位模块壳（编码独立）。接入点默认门户后端，可用 {@code app.modules.network.api-base} 单独配置。
 */
@Component
public class NetworkModule extends AbstractRoleModule {

    public NetworkModule(@Value("${app.modules.network.api-base:/api}") String apiBase) {
        super(PortalRole.NETWORK, apiBase);
    }
}
