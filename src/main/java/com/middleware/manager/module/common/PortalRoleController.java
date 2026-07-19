package com.middleware.manager.module.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公开的岗位清单接口——前端首页岗位入口与各公共模块左侧岗位导航共用同一数据源，
 * 保证「岗位专属区域」的五大入口与「公共区域」的岗位筛选顺序、标识、名称完全一致。
 */
@RestController
@RequestMapping("/api/public/portal/roles")
public class PortalRoleController {

    @GetMapping
    public List<Map<String, String>> list() {
        return PortalRole.ordered().stream().map(role -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", role.getId());
            item.put("label", role.getLabel());
            item.put("category", role.getCategory());
            return item;
        }).collect(Collectors.toList());
    }
}
