package com.middleware.manager.domain;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_records")
public class ReviewRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 160)
    private String documentTitle;

    @Column(nullable = false, length = 40)
    private String documentType;

    @Column(length = 60)
    private String category;

    @Column(length = 120)
    private String software;

    @Column(length = 20)
    private String documentVersion;

    @Column(nullable = false, length = 80)
    private String submitterUsername;

    @Column(length = 80)
    private String submitterDisplayName;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Column(length = 80)
    private String reviewerUsername;

    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String reviewComment;

    @Lob
    @Column(name = "previous_content")
    private String previousContent;

    @Lob
    @Column(name = "current_content", nullable = false)
    private String currentContent;

    @PrePersist
    void prePersist() {
        if (submittedAt == null) submittedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSoftware() { return software; }
    public void setSoftware(String software) { this.software = software; }
    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }
    public String getSubmitterUsername() { return submitterUsername; }
    public void setSubmitterUsername(String submitterUsername) { this.submitterUsername = submitterUsername; }
    public String getSubmitterDisplayName() { return submitterDisplayName; }
    public void setSubmitterDisplayName(String submitterDisplayName) { this.submitterDisplayName = submitterDisplayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReviewerUsername() { return reviewerUsername; }
    public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getPreviousContent() { return previousContent; }
    public void setPreviousContent(String previousContent) { this.previousContent = previousContent; }
    public String getCurrentContent() { return currentContent; }
    public void setCurrentContent(String currentContent) { this.currentContent = currentContent; }
}
