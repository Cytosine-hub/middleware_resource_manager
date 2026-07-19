package com.middleware.manager.module.spi;

import com.middleware.manager.module.common.PortalRole;

/**
 * 岗位模块壳的公共基类——集中处理 apiBase 归一化，五大岗位模块复用，保证行为一致（TC-07）。
 */
public abstract class AbstractRoleModule implements RoleModule {

    /** 门户后端默认接入点。 */
    public static final String PORTAL_API_BASE = "/api";

    private final PortalRole role;
    private final String apiBase;

    protected AbstractRoleModule(PortalRole role, String apiBase) {
        this.role = role;
        this.apiBase = (apiBase == null || apiBase.isBlank()) ? PORTAL_API_BASE : apiBase.trim();
    }

    @Override
    public PortalRole role() {
        return role;
    }

    @Override
    public String apiBase() {
        return apiBase;
    }
}
