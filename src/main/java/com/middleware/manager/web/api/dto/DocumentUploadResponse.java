package com.middleware.manager.web.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocumentUploadResponse {
    private String content;
    private String title;
    private List<String> images;

    public DocumentUploadResponse(String content, String title, List<String> images) {
        this.content = content;
        this.title = title;
        this.images = images;
    }
}
