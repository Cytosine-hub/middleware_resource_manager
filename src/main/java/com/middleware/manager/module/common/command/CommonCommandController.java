package com.middleware.manager.module.common.command;

import com.middleware.manager.module.common.command.dto.CommonCommandRequest;
import com.middleware.manager.module.common.command.dto.CommonCommandResponse;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.web.api.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 常用命令 REST 接口——门户后端提供的可复用能力，各岗位模块通过 {@code category} 复用同一套接口/数据。
 *
 * <p>读接口按岗位分类筛选（公共/岗位模块页面共用）；写接口沿用核心的 category 限权，
 * 只有系统管理员或对应岗位管理岗可维护本岗位的命令。
 */
@RestController
@RequestMapping("/api/module/commands")
public class CommonCommandController {

    private final CommonCommandService service;
    private final PermissionService permissionService;

    public CommonCommandController(CommonCommandService service, PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping
    public PageResponse<CommonCommandResponse> list(@RequestParam(defaultValue = "") String category,
                                                    @RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        Page<CommonCommand> result = service.list(category, keyword, page, size);
        List<CommonCommandResponse> content = result.getContent().stream()
                .map(CommonCommandResponse::from)
                .collect(Collectors.toList());
        return PageResponse.from(result, content);
    }

    @GetMapping("/{id}")
    public CommonCommandResponse detail(@PathVariable Long id) {
        return CommonCommandResponse.from(service.get(id));
    }

    @PostMapping
    public CommonCommandResponse create(@Valid @RequestBody CommonCommandRequest req, Authentication auth) {
        checkCategoryAccess(req.getCategory(), auth);
        return CommonCommandResponse.from(service.create(
                req.getCategory(), req.getTitle(), req.getCommand(), req.getDescription(), req.getTag()));
    }

    @PutMapping("/{id}")
    public CommonCommandResponse update(@PathVariable Long id,
                                        @Valid @RequestBody CommonCommandRequest req,
                                        Authentication auth) {
        checkCategoryAccess(service.get(id).getCategory(), auth);
        checkCategoryAccess(req.getCategory(), auth);
        return CommonCommandResponse.from(service.update(
                id, req.getCategory(), req.getTitle(), req.getCommand(), req.getDescription(), req.getTag()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication auth) {
        checkCategoryAccess(service.get(id).getCategory(), auth);
        service.delete(id);
    }

    private void checkCategoryAccess(String category, Authentication auth) {
        if (!permissionService.canManageCategory(auth, category)) {
            throw new IllegalArgumentException("无权维护该岗位的常用命令");
        }
    }
}
