package com.middleware.manager.web.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocumentUploadResponse {
    private String content;
    private String title;
    private List<String> images;
    private String storedFileName;
    private String originalFileName;

    public DocumentUploadResponse(String content, String title, List<String> images) {
        this.content = content;
        this.title = title;
        this.images = images;
    }

    public DocumentUploadResponse(String content, String title, List<String> images,
                                  String storedFileName, String originalFileName) {
        this.content = content;
        this.title = title;
        this.images = images;
        this.storedFileName = storedFileName;
        this.originalFileName = originalFileName;
    }
}
