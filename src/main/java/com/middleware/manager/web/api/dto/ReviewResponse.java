package com.middleware.manager.web.api.dto;

import com.middleware.manager.domain.ReviewRecord;
import lombok.Data;

import java.time.LocalDateTime;

@Data
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
}
