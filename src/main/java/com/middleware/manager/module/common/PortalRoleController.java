package com.middleware.manager.module.common;

import com.middleware.manager.module.spi.RoleModule;
import com.middleware.manager.module.spi.RoleModuleRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开的岗位清单接口——前端首页岗位入口与各公共模块左侧岗位导航共用同一数据源，
 * 保证「岗位专属区域」的五大入口与「公共区域」的岗位筛选顺序、标识、名称完全一致。
 *
 * <p>每项附带 {@code apiBase}：该岗位模块的后端接入点（默认门户后端 {@code /api}，可按岗位单独配置为
 * 独立后端服务），前端岗位模块据此决定连接门户后端还是岗位自己的后端（TC-06）。
 */
@RestController
@RequestMapping("/api/public/portal/roles")
public class PortalRoleController {

    private final RoleModuleRegistry roleModuleRegistry;

    public PortalRoleController(RoleModuleRegistry roleModuleRegistry) {
        this.roleModuleRegistry = roleModuleRegistry;
    }

    @GetMapping
    public List<Map<String, String>> list() {
        return roleModuleRegistry.ordered().stream().map(this::toItem).collect(Collectors.toList());
    }

    private Map<String, String> toItem(RoleModule module) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("id", module.roleId());
        item.put("label", module.label());
        item.put("category", module.category());
        item.put("apiBase", module.apiBase());
        return item;
    }
}
