package com.middleware.manager.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public Role getCurrentRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            try {
                return Role.fromAuthority(authority.getAuthority());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public boolean isAdmin(Authentication authentication) {
        Role role = getCurrentRole(authentication);
        return role != null && role.isAdmin();
    }

    public boolean canManageCategory(Authentication authentication, String category) {
        Role role = getCurrentRole(authentication);
        return role != null && role.canManageCategory(category);
    }

    public String getManagedCategory(Authentication authentication) {
        Role role = getCurrentRole(authentication);
        return role != null ? role.getManagedCategory() : null;
    }

    public String requireManagedCategory(Authentication authentication) {
        String category = getManagedCategory(authentication);
        if (category == null) {
            throw new IllegalArgumentException("当前角色无管理分类权限");
        }
        return category;
    }
}
