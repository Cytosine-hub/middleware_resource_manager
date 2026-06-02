package com.middleware.manager.wiki.entity;

import java.time.LocalDateTime;

public class WikiIngestLog {
    private Long id;
    private Long sourceId;
    private Long operatorId;
    private int pagesCreated;
    private int pagesUpdated;
    private int linksCreated;
    private int contradictionsFound;
    private String llmModel;
    private int llmTokensUsed;
    private int durationMs;
    private String status; // SUCCESS, PARTIAL, FAILED
    private String errorDetail;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public int getPagesCreated() { return pagesCreated; }
    public void setPagesCreated(int pagesCreated) { this.pagesCreated = pagesCreated; }
    public int getPagesUpdated() { return pagesUpdated; }
    public void setPagesUpdated(int pagesUpdated) { this.pagesUpdated = pagesUpdated; }
    public int getLinksCreated() { return linksCreated; }
    public void setLinksCreated(int linksCreated) { this.linksCreated = linksCreated; }
    public int getContradictionsFound() { return contradictionsFound; }
    public void setContradictionsFound(int contradictionsFound) { this.contradictionsFound = contradictionsFound; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public int getLlmTokensUsed() { return llmTokensUsed; }
    public void setLlmTokensUsed(int llmTokensUsed) { this.llmTokensUsed = llmTokensUsed; }
    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorDetail() { return errorDetail; }
    public void setErrorDetail(String errorDetail) { this.errorDetail = errorDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
