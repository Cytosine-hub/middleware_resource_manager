package com.middleware.manager.module.middleware;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.spi.AbstractRoleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 中间件岗位模块壳（编码独立，见 agent.md §4b）。承接门户「常用命令」通用能力。
 * 接入点默认门户后端，可用 {@code app.modules.middleware.api-base} 单独指向独立后端服务。
 */
@Component
public class MiddlewareModule extends AbstractRoleModule {

    public MiddlewareModule(@Value("${app.modules.middleware.api-base:/api}") String apiBase) {
        super(PortalRole.MIDDLEWARE, apiBase);
    }
}
