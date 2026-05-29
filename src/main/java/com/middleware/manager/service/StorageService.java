package com.middleware.manager.service;

import com.middleware.manager.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageService.class);
    private static final Pattern UNSAFE_CATEGORY_CHARS = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");

    private final Path rootLocation;

    public StorageService(StorageProperties properties) throws IOException {
        this.rootLocation = Paths.get(properties.getLocation()).toAbsolutePath().normalize();
        Files.createDirectories(rootLocation);
    }

    public StoredFile store(MultipartFile file, String middlewareName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传安装介质文件");
        }

        String originalFileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "resource.bin" : file.getOriginalFilename());
        String extension = resolveExtension(originalFileName);
        String storedFileName = resolveCategory(middlewareName) + "/" + UUID.randomUUID() + extension;
        Path destination = resolveStoragePath(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            Files.createDirectories(destination.getParent());
            Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("文件保存失败", ex);
        }

        return new StoredFile(storedFileName, originalFileName, file.getContentType(), file.getSize());
    }

    public StoredFile store(Path filePath, String middlewareName) {
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("文件不存在");
        }

        String originalFileName = filePath.getFileName().toString();
        String extension = resolveExtension(originalFileName);
        String storedFileName = resolveCategory(middlewareName) + "/" + UUID.randomUUID() + extension;
        Path destination = resolveStoragePath(storedFileName);

        try {
            Files.createDirectories(destination.getParent());
            Files.copy(filePath, destination, StandardCopyOption.REPLACE_EXISTING);
            long size = Files.size(filePath);
            String contentType = Files.probeContentType(filePath);
            return new StoredFile(storedFileName, originalFileName, contentType, size);
        } catch (IOException ex) {
            throw new IllegalStateException("文件保存失败", ex);
        }
    }

    public StoredFile importFile(Path sourceFile, String middlewareName) {
        Path normalizedSource = sourceFile.toAbsolutePath().normalize();
        if (!Files.exists(normalizedSource) || !Files.isRegularFile(normalizedSource)) {
            throw new IllegalArgumentException("待导入文件不存在");
        }

        String originalFileName = StringUtils.cleanPath(normalizedSource.getFileName().toString());

        try {
            String contentType = Files.probeContentType(normalizedSource);
            long size = Files.size(normalizedSource);

            if (normalizedSource.startsWith(rootLocation)) {
                String relativePath = rootLocation.relativize(normalizedSource).toString().replace("\\", "/");
                return new StoredFile(relativePath, originalFileName, contentType, size);
            }

            String storedFileName = resolveCategory(middlewareName) + "/" + UUID.randomUUID() + resolveExtension(originalFileName);
            Path destination = resolveStoragePath(storedFileName);
            Files.createDirectories(destination.getParent());
            Files.copy(normalizedSource, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(storedFileName, originalFileName, contentType, size);
        } catch (IOException ex) {
            throw new IllegalStateException("导入文件到受管目录失败", ex);
        }
    }

    public Resource loadAsResource(String storedFileName) {
        try {
            Path file = resolveStoragePath(storedFileName);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
        } catch (IOException ignored) {
        }
        throw new IllegalArgumentException("文件不存在或无法读取");
    }

    public void deleteIfExists(String storedFileName) {
        if (!StringUtils.hasText(storedFileName)) {
            return;
        }
        Path file = resolveStoragePath(storedFileName);
        try {
            clearReadOnlyAttribute(file);
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            LOGGER.warn("delete file failed path={}", file, ex);
            throw new IllegalStateException("删除历史文件失败：" + file, ex);
        }
    }

    public String moveToMiddlewareDirectory(String storedFileName, String middlewareName) {
        if (!StringUtils.hasText(storedFileName)) {
            return storedFileName;
        }

        String normalizedPath = storedFileName.replace("\\", "/");
        String targetCategory = resolveCategory(middlewareName);
        if (normalizedPath.startsWith(targetCategory + "/")) {
            return normalizedPath;
        }

        Path source = resolveStoragePath(normalizedPath);
        if (!Files.exists(source)) {
            return normalizedPath;
        }

        String targetPath = targetCategory + "/" + source.getFileName();
        Path destination = resolveStoragePath(targetPath);
        try {
            Files.createDirectories(destination.getParent());
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            return targetPath;
        } catch (IOException ex) {
            throw new IllegalStateException("迁移文件目录失败", ex);
        }
    }

    public Path getRootLocation() {
        return rootLocation;
    }

    private Path resolveStoragePath(String storedFileName) {
        Path destination = rootLocation.resolve(storedFileName).normalize();
        if (!destination.startsWith(rootLocation)) {
            throw new IllegalArgumentException("非法文件路径");
        }
        return destination;
    }

    private void clearReadOnlyAttribute(Path file) throws IOException {
        if (!Files.exists(file) || Files.isWritable(file)) {
            return;
        }
        try {
            Files.setAttribute(file, "dos:readonly", false);
            LOGGER.info("cleared readonly attribute path={}", file);
        } catch (UnsupportedOperationException ex) {
            if (!file.toFile().setWritable(true)) {
                throw new IOException("无法移除文件只读属性：" + file, ex);
            }
            LOGGER.info("cleared readonly attribute path={}", file);
        }
    }

    private String resolveCategory(String middlewareName) {
        String candidate = StringUtils.hasText(middlewareName) ? middlewareName.trim() : "uncategorized";
        candidate = Normalizer.normalize(candidate, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        candidate = UNSAFE_CATEGORY_CHARS.matcher(candidate).replaceAll("-");
        candidate = candidate.replaceAll("^-+|-+$", "");
        return candidate.trim().isEmpty() ? "uncategorized" : candidate;
    }

    private String resolveExtension(String originalFileName) {
        int lastDot = originalFileName.lastIndexOf('.');
        return lastDot >= 0 ? originalFileName.substring(lastDot) : "";
    }

    public static class StoredFile {
        private final String storedFileName;
        private final String originalFileName;
        private final String contentType;
        private final long size;

        public StoredFile(String storedFileName, String originalFileName, String contentType, long size) {
            this.storedFileName = storedFileName;
            this.originalFileName = originalFileName;
            this.contentType = contentType;
            this.size = size;
        }

        public String storedFileName() {
            return storedFileName;
        }

        public String originalFileName() {
            return originalFileName;
        }

        public String contentType() {
            return contentType;
        }

        public long size() {
            return size;
        }
    }
}
