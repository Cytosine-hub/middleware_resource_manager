package com.middleware.manager.web.api;

import com.middleware.manager.config.StorageProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
public class ImageController {

    private final Path imageStoragePath;

    public ImageController(StorageProperties storageProperties) throws IOException {
        this.imageStoragePath = Paths.get(storageProperties.getLocation(), "images").toAbsolutePath().normalize();
        Files.createDirectories(this.imageStoragePath);
    }

    @PostMapping("/api/admin/images/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件上传");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        if (ext.isEmpty()) ext = ".png";

        String storedName = UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, imageStoragePath.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("图片保存失败", e);
        }

        return Collections.singletonMap("url", "/files/images/" + storedName);
    }

    @GetMapping("/files/images/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        try {
            Path file = imageStoragePath.resolve(filename).normalize();
            if (!file.startsWith(imageStoragePath)) {
                return ResponseEntity.badRequest().build();
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = MediaType.IMAGE_PNG;
                try {
                    String probe = Files.probeContentType(file);
                    if (probe != null) mediaType = MediaType.parseMediaType(probe);
                } catch (Exception ignored) {}
                return ResponseEntity.ok().contentType(mediaType).body(resource);
            }
        } catch (MalformedURLException ignored) {}
        return ResponseEntity.notFound().build();
    }
}
