package com.middleware.manager.web.controller;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class FileDownloadController {

    private final ReleaseService releaseService;
    private final StorageService storageService;

    public FileDownloadController(ReleaseService releaseService, StorageService storageService) {
        this.releaseService = releaseService;
        this.storageService = storageService;
    }

    @GetMapping("/files/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        ReleaseAsset release = findPublishedRelease(token);
        releaseService.incrementDownloadCount(release);

        Resource resource = storageService.loadAsResource(release.getStoredFileName());
        MediaType mediaType;
        try {
            mediaType = release.getContentType() != null
                    ? MediaType.parseMediaType(release.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(release.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(release.getOriginalFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }

    private ReleaseAsset findPublishedRelease(String token) {
        try {
            return releaseService.getPublishedRelease(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }
}
