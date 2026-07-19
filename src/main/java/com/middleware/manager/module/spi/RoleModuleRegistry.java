package com.middleware.manager.module.spi;

import com.middleware.manager.module.common.PortalRole;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 岗位模块注册表——把五个独立的 {@link RoleModule} 壳聚合起来，按门户约定顺序对外提供，
 * 供门户后端（如岗位清单接口）统一读取各岗位的接入点与元数据。
 */
@Component
public class RoleModuleRegistry {

    private final Map<String, RoleModule> byId;

    public RoleModuleRegistry(List<RoleModule> modules) {
        this.byId = modules.stream().collect(Collectors.toMap(RoleModule::roleId, Function.identity()));
    }

    /** 按门户固定顺序返回已注册的岗位模块。 */
    public List<RoleModule> ordered() {
        return PortalRole.ordered().stream()
                .map(role -> byId.get(role.getId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public RoleModule get(String roleId) {
        return byId.get(roleId);
    }
}
