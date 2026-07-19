package com.middleware.manager.module.spi;

import com.middleware.manager.module.common.PortalRole;

/**
 * 岗位模块 SPI——五大岗位（中间件/数据库/主机/网络/网络安全）在编码层面各自独立的模块壳。
 *
 * <p>每个岗位在自己的包（{@code module/<角色>/}）内提供一个实现，彼此不互相依赖：这是 TC-06
 * 「岗位模块编码独立、可单独维护」的落点。共用能力（如常用命令、岗位契约）仍集中在
 * {@code module/common/}，避免重复实现（TC-07）。
 *
 * <p>{@link #apiBase()} 是该岗位模块的后端接入点：默认使用门户后端 {@code /api}，也可通过配置
 * {@code app.modules.<角色id>.api-base} 指向岗位自己的独立后端服务；因为每个岗位读各自的配置键，
 * 单个岗位的接入点变化不会影响其他岗位（TC-06 第 4 步）。
 */
public interface RoleModule {

    /** 本模块对应的门户岗位契约。 */
    PortalRole role();

    /** 本岗位模块的后端接入点（默认门户后端 {@code /api}，可配置为独立后端）。 */
    String apiBase();

    default String roleId() {
        return role().getId();
    }

    default String label() {
        return role().getLabel();
    }

    default String category() {
        return role().getCategory();
    }
}
