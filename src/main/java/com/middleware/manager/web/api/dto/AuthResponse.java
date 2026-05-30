package com.middleware.manager.web.api.dto;

import java.time.LocalDateTime;

public class AuthResponse {
    private String username;
    private String displayName;
    private String role;
    private String token;
    private LocalDateTime expiresAt;

    public AuthResponse(String username, String displayName, String role) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
    }

    public AuthResponse(String username, String displayName, String role, String token, LocalDateTime expiresAt) {
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
