package com.middleware.manager.module.common;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 集成中心门户五大岗位的统一契约（岗位专属区域 + 公共区域左侧岗位导航共用）。
 *
 * <p>每个岗位模块（中间件/数据库/主机/网络/网络安全）在编码层面独立，但都通过本枚举获得一致的
 * 岗位标识（id）、展示名（label）与数据范围（category），保证前后端与各模块 UI 的一致性。
 *
 * <p>注意：网络安全岗对外展示为「网络安全」，但其数据范围沿用系统既有的 {@code 安全} 分类，
 * 与 {@link com.middleware.manager.security.Role} 中的 managedCategory 对齐。
 */
public enum PortalRole {
    MIDDLEWARE("middleware", "中间件", "中间件"),
    DATABASE("database", "数据库", "数据库"),
    HOST("host", "主机", "主机"),
    NETWORK("network", "网络", "网络"),
    SECURITY("security", "网络安全", "安全");

    private final String id;
    private final String label;
    private final String category;

    PortalRole(String id, String label, String category) {
        this.id = id;
        this.label = label;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getCategory() {
        return category;
    }

    /** 门户约定的岗位固定展示顺序。 */
    public static List<PortalRole> ordered() {
        return Arrays.asList(MIDDLEWARE, DATABASE, HOST, NETWORK, SECURITY);
    }

    /** 全部岗位对应的数据范围分类。 */
    public static List<String> categories() {
        return ordered().stream().map(PortalRole::getCategory).collect(Collectors.toList());
    }

    public static PortalRole fromId(String id) {
        for (PortalRole role : values()) {
            if (role.id.equals(id)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知岗位模块: " + id);
    }

    /** 判断分类是否属于门户五大岗位之一。 */
    public static boolean isRoleCategory(String category) {
        return categories().contains(category);
    }
}
