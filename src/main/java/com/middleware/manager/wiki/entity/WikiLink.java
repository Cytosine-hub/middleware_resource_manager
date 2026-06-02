package com.middleware.manager.wiki.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WikiLink {
    private Long id;
    private Long fromPageId;
    private Long toPageId;
    private String linkType; // REFERENCES, CONTRADICTS, SPECIALIZES, DEPENDS_ON, RELATED
    private BigDecimal confidence;
    private String context;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getFromPageId() { return fromPageId; }
    public void setFromPageId(Long fromPageId) { this.fromPageId = fromPageId; }
    public Long getToPageId() { return toPageId; }
    public void setToPageId(Long toPageId) { this.toPageId = toPageId; }
    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
