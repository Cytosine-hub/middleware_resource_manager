package com.middleware.manager.wiki.entity;

import java.time.LocalDateTime;

public class WikiPage {
    private Long id;
    private String title;
    private String pageType; // ENTITY, CONCEPT, RUNBOOK, EXPERIENCE, STANDARD, SYNTHESIS, OVERVIEW
    private String category;
    private String software;
    private String version;
    private String content;
    private String summary;
    private String sourceRefs; // JSON
    private String status; // DRAFT, ACTIVE, STALE, CONTRADICTED
    private String contradictionNote;
    private String compiledBy;
    private LocalDateTime compiledAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPageType() { return pageType; }
    public void setPageType(String pageType) { this.pageType = pageType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSourceRefs() { return sourceRefs; }
    public void setSourceRefs(String sourceRefs) { this.sourceRefs = sourceRefs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContradictionNote() { return contradictionNote; }
    public void setContradictionNote(String contradictionNote) { this.contradictionNote = contradictionNote; }
    public String getCompiledBy() { return compiledBy; }
    public void setCompiledBy(String compiledBy) { this.compiledBy = compiledBy; }
    public LocalDateTime getCompiledAt() { return compiledAt; }
    public void setCompiledAt(LocalDateTime compiledAt) { this.compiledAt = compiledAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
