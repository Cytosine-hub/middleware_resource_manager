package com.middleware.manager.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum Role {
    系统管理员("ROLE_SYS_ADMIN", null),
    中间件管理岗("ROLE_MIDDLEWARE_MGR", "中间件"),
    数据库管理岗("ROLE_DATABASE_MGR", "数据库"),
    主机管理岗("ROLE_HOST_MGR", "主机"),
    网络管理岗("ROLE_NETWORK_MGR", "网络"),
    网络安全岗("ROLE_SECURITY_MGR", "安全"),
    开发经理("ROLE_DEV_MGR", null),
    运维经理("ROLE_OPS_MGR", null);

    private final String authority;
    private final String managedCategory;

    Role(String authority, String managedCategory) {
        this.authority = authority;
        this.managedCategory = managedCategory;
    }

    public String getAuthority() {
        return authority;
    }

    public String getManagedCategory() {
        return managedCategory;
    }

    public boolean isAdmin() {
        return this == 系统管理员;
    }

    public boolean isManager() {
        return managedCategory != null;
    }

    public boolean isReadOnly() {
        return this == 开发经理 || this == 运维经理;
    }

    /** 管理岗是否有权操作指定分类的资源 */
    public boolean canManageCategory(String category) {
        if (isAdmin()) return true;
        if (!isManager()) return false;
        return managedCategory.equals(category);
    }

    public static Role fromAuthority(String authority) {
        for (Role role : values()) {
            if (role.authority.equals(authority)) return role;
        }
        throw new IllegalArgumentException("Unknown authority: " + authority);
    }

    public static Role fromDisplayName(String displayName) {
        for (Role role : values()) {
            if (role.name().equals(displayName)) return role;
        }
        throw new IllegalArgumentException("Unknown role: " + displayName);
    }

    public static Set<String> allAuthorities() {
        return Arrays.stream(values()).map(Role::getAuthority).collect(Collectors.toSet());
    }
}
