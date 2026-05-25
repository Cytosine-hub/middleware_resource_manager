package com.middleware.manager.knowledge.agent;

import java.time.LocalDateTime;

public class ChatMessage {

    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String referencesText;
    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getReferencesText() {
        return referencesText;
    }

    public void setReferencesText(String referencesText) {
        this.referencesText = referencesText;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
