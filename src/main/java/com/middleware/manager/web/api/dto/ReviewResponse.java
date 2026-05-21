package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ReviewRecord;

import java.time.LocalDateTime;

public class ReviewResponse {
    private Long id;
    private Long documentId;
    private String documentTitle;
    private String documentType;
    private String category;
    private String software;
    private String documentVersion;
    private String submitterUsername;
    private String submitterDisplayName;
    private String status;
    private String statusLabel;
    private LocalDateTime submittedAt;
    private String reviewerUsername;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private String diff;
    private String currentContent;

    public static ReviewResponse from(ReviewRecord record) {
        ReviewResponse r = new ReviewResponse();
        r.setId(record.getId());
        r.setDocumentId(record.getDocumentId());
        r.setDocumentTitle(record.getDocumentTitle());
        r.setDocumentType(record.getDocumentType());
        r.setCategory(record.getCategory());
        r.setSoftware(record.getSoftware());
        r.setDocumentVersion(record.getDocumentVersion());
        r.setSubmitterUsername(record.getSubmitterUsername());
        r.setSubmitterDisplayName(record.getSubmitterDisplayName());
        r.setStatus(record.getStatus());
        r.setStatusLabel(computeStatusLabel(record.getStatus()));
        r.setSubmittedAt(record.getSubmittedAt());
        r.setReviewerUsername(record.getReviewerUsername());
        r.setReviewedAt(record.getReviewedAt());
        r.setReviewComment(record.getReviewComment());
        r.setCurrentContent(record.getCurrentContent());
        return r;
    }

    private static String computeStatusLabel(String status) {
        switch (status) {
            case "PENDING": return "待审核";
            case "APPROVED": return "已通过";
            case "REJECTED": return "已驳回";
            default: return status;
        }
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
    public String getStatusLabel() { return statusLabel; }
    public void setStatusLabel(String statusLabel) { this.statusLabel = statusLabel; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReviewerUsername() { return reviewerUsername; }
    public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }
    public String getCurrentContent() { return currentContent; }
    public void setCurrentContent(String currentContent) { this.currentContent = currentContent; }
}
