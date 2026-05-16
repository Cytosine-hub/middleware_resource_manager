package com.middleware.manager.web.api;

import com.middleware.manager.domain.AdminAccount;
import com.middleware.manager.security.Role;
import com.middleware.manager.service.AdminAccountService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminAccountService adminAccountService;

    public AdminUserController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping
    public List<Map<String, Object>> listUsers() {
        return adminAccountService.listUsers().stream()
                .map(this::toUserMap)
                .collect(Collectors.toList());
    }

    @PostMapping
    public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request) {
        AdminAccount account = adminAccountService.createUser(
                request.username, request.displayName, request.password, request.role);
        return toUserMap(account);
    }

    @PutMapping("/{id}/role")
    public Map<String, Object> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody UpdateRoleRequest request) {
        AdminAccount account = adminAccountService.updateUserRole(id, request.role);
        return toUserMap(account);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable Long id,
                              @Valid @RequestBody ResetPasswordRequest request) {
        adminAccountService.resetPassword(id, request.newPassword);
    }

    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        adminAccountService.deleteUser(id);
    }

    @GetMapping("/roles")
    public List<Map<String, String>> listRoles() {
        return Arrays.stream(Role.values()).map(role -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("name", role.name());
            map.put("authority", role.getAuthority());
            map.put("category", role.getManagedCategory() != null ? role.getManagedCategory() : "");
            return map;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> toUserMap(AdminAccount account) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", account.getId());
        map.put("username", account.getUsername());
        map.put("displayName", account.getDisplayName());
        map.put("role", account.getRole());
        map.put("createdAt", account.getCreatedAt());
        return map;
    }

    static class CreateUserRequest {
        @NotBlank @Size(min = 2, max = 60)
        public String username;
        public String displayName;
        @NotBlank @Size(min = 6, max = 64)
        public String password;
        @NotBlank
        public String role;
    }

    static class UpdateRoleRequest {
        @NotBlank
        public String role;
    }

    static class ResetPasswordRequest {
        @NotBlank @Size(min = 6, max = 64)
        public String newPassword;
    }
}
