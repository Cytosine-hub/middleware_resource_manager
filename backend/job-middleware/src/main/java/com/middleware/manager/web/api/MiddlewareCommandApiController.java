package com.middleware.manager.web.api;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.MiddlewareCommand;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.exception.ForbiddenException;
import com.middleware.manager.security.PermissionService;
import com.middleware.manager.service.MiddlewareCommandService;
import com.middleware.manager.web.api.dto.MiddlewareCommandImportResult;
import com.middleware.manager.web.api.dto.MiddlewareCommandRequest;
import com.middleware.manager.web.api.dto.MiddlewareCommandResponse;
import com.middleware.manager.web.api.dto.MiddlewareCommandTransferItem;
import com.middleware.manager.web.api.dto.SoftwareTypeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/middleware-commands")
public class MiddlewareCommandApiController {
    private final MiddlewareCommandService service;
    private final PermissionService permissionService;

    public MiddlewareCommandApiController(MiddlewareCommandService service,
                                          PermissionService permissionService) {
        this.service = service;
        this.permissionService = permissionService;
    }

    @GetMapping("/types")
    public List<SoftwareTypeResponse> listTypes() {
        return service.listTypes().stream().map(SoftwareTypeResponse::from).toList();
    }

    @GetMapping
    public List<MiddlewareCommandResponse> listCommands(
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String category) {
        List<MiddlewareCommand> commands = category != null && !category.isBlank()
                ? service.listCommandsByCategory(category)
                : service.listCommands(typeId);
        return commands.stream().map(MiddlewareCommandResponse::from).toList();
    }

    @GetMapping("/export")
    public List<MiddlewareCommandTransferItem> exportCommands(Authentication authentication) {
        requireSystemAdmin(authentication);
        return service.exportCommands();
    }

    @PostMapping("/import")
    public MiddlewareCommandImportResult importCommands(
            @NotEmpty @RequestBody List<@Valid MiddlewareCommandTransferItem> items,
            Authentication authentication) {
        requireSystemAdmin(authentication);
        return service.importCommands(items);
    }

    @PostMapping
    public MiddlewareCommandResponse create(@Valid @RequestBody MiddlewareCommandRequest request,
                                            Authentication authentication) {
        SoftwareType type = service.getSoftwareType(request.getSoftwareTypeId());
        checkCategoryAccess(authentication, type.getCategory());
        return MiddlewareCommandResponse.from(service.create(
                request.getSoftwareTypeId(), request.getCommandFormat(),
                request.getBriefDescription(), request.getDetailedDescription(),
                request.getCategories(), request.getSortOrder()));
    }

    @PutMapping("/{id}")
    public MiddlewareCommandResponse update(@PathVariable Long id,
                                            @Valid @RequestBody MiddlewareCommandRequest request,
                                            Authentication authentication) {
        MiddlewareCommand existing = service.get(id);
        checkCategoryAccess(authentication,
                service.getSoftwareType(existing.getSoftwareTypeId()).getCategory());
        SoftwareType targetType = service.getSoftwareType(request.getSoftwareTypeId());
        checkCategoryAccess(authentication, targetType.getCategory());
        return MiddlewareCommandResponse.from(service.update(
                id, request.getSoftwareTypeId(), request.getCommandFormat(),
                request.getBriefDescription(), request.getDetailedDescription(),
                request.getCategories(), request.getSortOrder()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        MiddlewareCommand existing = service.get(id);
        SoftwareType type = service.getSoftwareType(existing.getSoftwareTypeId());
        checkCategoryAccess(authentication, type.getCategory());
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private void checkCategoryAccess(Authentication authentication, String category) {
        if (!permissionService.canManageCategory(authentication, category)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }

    private void requireSystemAdmin(Authentication authentication) {
        if (!permissionService.isAdmin(authentication)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, ErrorMessages.FORBIDDEN);
        }
    }
}
