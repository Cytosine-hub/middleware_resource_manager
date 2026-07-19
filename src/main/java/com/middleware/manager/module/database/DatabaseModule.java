package com.middleware.manager.module.database;

import com.middleware.manager.module.common.PortalRole;
import com.middleware.manager.module.spi.AbstractRoleModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据库岗位模块壳（编码独立）。接入点默认门户后端，可用 {@code app.modules.database.api-base} 单独配置。
 */
@Component
public class DatabaseModule extends AbstractRoleModule {

    public DatabaseModule(@Value("${app.modules.database.api-base:/api}") String apiBase) {
        super(PortalRole.DATABASE, apiBase);
    }
}
