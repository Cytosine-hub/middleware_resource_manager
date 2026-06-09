package com.middleware.manager.service;

import com.middleware.manager.config.StorageProperties;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.web.api.dto.DocumentUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档转换服务：支持 Word (.doc/.docx) 和 Markdown (.md) 文件上传
 */
@Service
@Slf4j
public class DocumentConversionService {

    private static final Set<String> WORD_EXTENSIONS = new HashSet<>(Arrays.asList(".doc", ".docx"));
    private static final Set<String> MARKDOWN_EXTENSIONS = new HashSet<>(Arrays.asList(".md", ".markdown"));
    private static final String IMAGE_URL_PREFIX = "/files/images/";
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final int MAX_TITLE_LENGTH = 50;
    private static final int MAX_LIST_INDENT = 3;
    private static final String INDENT_UNIT = "  ";
    private static final int BODY_CONTENT_HANDLER_UNLIMITED = -1;

    private final Path imageStoragePath;
    private final AutoDetectParser tikaParser;

    public DocumentConversionService(StorageProperties storageProperties) throws IOException {
        this.imageStoragePath = Paths.get(storageProperties.getLocation(), "images").toAbsolutePath().normalize();
        Files.createDirectories(this.imageStoragePath);
        this.tikaParser = new AutoDetectParser();
    }

    /**
     * 转换文档
     * @param file 上传的文件
     * @param convertToMarkdown 是否转换为 Markdown（仅 Word 文档有效）
     * @return 转换结果：content, title, images
     */
    public DocumentUploadResponse convert(MultipartFile file, boolean convertToMarkdown) {
        validateFile(file);
        String fileName = file.getOriginalFilename();
        String ext = getExtension(fileName);

        try {
            if (MARKDOWN_EXTENSIONS.contains(ext)) {
                return convertMarkdown(file);
            } else if (".docx".equals(ext)) {
                return convertDocx(file, convertToMarkdown);
            } else if (".doc".equals(ext)) {
                return convertDoc(file, convertToMarkdown);
            } else {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "不支持的文件格式：" + ext);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文档转换失败 fileName={}", fileName, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "文档转换失败：" + e.getMessage());
        }
    }

    /** Markdown 文件：直接读取 */
    private DocumentUploadResponse convertMarkdown(MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), "UTF-8");
        String title = extractMarkdownTitle(content);
        log.info("Markdown 文件已读取 fileName={}", file.getOriginalFilename());
        return buildResult(content, title, Collections.emptyList());
    }

    /** .docx 转换：使用 POI XWPFDocument 提取格式和图片 */
    private DocumentUploadResponse convertDocx(MultipartFile file, boolean convertToMarkdown) throws Exception {
        List<String> images = new ArrayList<>();

        try (InputStream is = file.getInputStream(); XWPFDocument doc = new XWPFDocument(is)) {
            // 提取并保存嵌入图片
            Map<String, String> imageMapping = extractAndSaveImages(doc, images);

            String content;
            String title;
            if (convertToMarkdown) {
                StringBuilder sb = new StringBuilder();
                for (IBodyElement element : doc.getBodyElements()) {
                    if (element instanceof XWPFParagraph) {
                        processParagraph((XWPFParagraph) element, sb, imageMapping);
                    } else if (element instanceof XWPFTable) {
                        processTable((XWPFTable) element, sb, imageMapping);
                    }
                }
                content = sb.toString().trim();
                title = extractFirstHeading(content);
            } else {
                // 不转换：提取纯文本
                BodyContentHandler handler = new BodyContentHandler(BODY_CONTENT_HANDLER_UNLIMITED);
                Metadata metadata = new Metadata();
                tikaParser.parse(file.getInputStream(), handler, metadata);
                content = handler.toString();
                title = content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;
            }

            log.info("DOCX 文件已转换 fileName={}, convertToMarkdown={}, images={}",
                    file.getOriginalFilename(), convertToMarkdown, images.size());
            return buildResult(content, title, images);
        }
    }

    /** .doc 转换：使用 Tika 提取纯文本（旧格式不支持富文本转换） */
    private DocumentUploadResponse convertDoc(MultipartFile file, boolean convertToMarkdown) throws Exception {
        List<String> images = new ArrayList<>();

        BodyContentHandler handler = new BodyContentHandler(BODY_CONTENT_HANDLER_UNLIMITED);
        Metadata metadata = new Metadata();
        try (InputStream is = file.getInputStream()) {
            tikaParser.parse(is, handler, metadata);
        }
        String content = handler.toString();
        String title = content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;

        log.info("DOC 文件已读取 fileName={}, length={}", file.getOriginalFilename(), content.length());
        return buildResult(content, title, images);
    }

    /** 从 DOCX 提取并保存图片，返回 oldRId → newUrl 映射 */
    private Map<String, String> extractAndSaveImages(XWPFDocument doc, List<String> images) throws IOException {
        Map<String, String> mapping = new HashMap<>();
        for (XWPFPictureData picture : doc.getAllPictures()) {
            String fileName = picture.getFileName();
            String ext = getExtension(fileName);
            if (ext.isEmpty()) ext = ".png";

            String storedName = UUID.randomUUID() + ext;
            Path target = imageStoragePath.resolve(storedName);
            Files.copy(picture.getPackagePart().getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

            String url = IMAGE_URL_PREFIX + storedName;
            images.add(url);

            // 建立原始文件名到新 URL 的映射
            mapping.put(fileName, url);
            mapping.put(picture.getPackagePart().getPartName().getName(), url);
        }
        return mapping;
    }

    /** 处理段落：识别标题、粗体、斜体、列表等 */
    private void processParagraph(XWPFParagraph para, StringBuilder sb, Map<String, String> imageMapping) {
        String style = para.getStyle();
        int headingLevel = getHeadingLevel(style);

        // 检查是否为列表项
        String numFmt = para.getNumFmt();
        boolean isBullet = "bullet".equals(numFmt);
        boolean isNumber = numFmt != null && !numFmt.equals("bullet") && !numFmt.isEmpty();

        // 处理列表缩进
        String indent = "";
        if (isBullet || isNumber) {
            int ilvl = 0;
            if (para.getCTP() != null && para.getCTP().getPPr() != null
                    && para.getCTP().getPPr().getNumPr() != null
                    && para.getCTP().getPPr().getNumPr().getIlvl() != null) {
                ilvl = (int) Math.min(para.getCTP().getPPr().getNumPr().getIlvl().getVal().longValue(), MAX_LIST_INDENT);
            }
            indent = INDENT_UNIT.repeat(ilvl);
        }

        // 处理段落内容
        StringBuilder line = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            // 处理图片
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                String picName = pic.getPictureData().getFileName();
                String url = imageMapping.get(picName);
                if (url != null) {
                    line.append("![](").append(url).append(")");
                }
            }

            String text = run.text();
            if (text == null || text.isEmpty()) continue;

            boolean bold = run.isBold();
            boolean italic = run.isItalic();
            boolean strike = run.isStrikeThrough();

            if (bold && italic) {
                text = "***" + text + "***";
            } else if (bold) {
                text = "**" + text + "**";
            } else if (italic) {
                text = "*" + text + "*";
            }
            if (strike) {
                text = "~~" + text + "~~";
            }
            line.append(text);
        }

        String text = line.toString().trim();
        if (text.isEmpty()) {
            sb.append("\n");
            return;
        }

        // 输出 Markdown
        if (headingLevel > 0 && headingLevel <= 3) {
            sb.append("#".repeat(headingLevel)).append(" ").append(text).append("\n\n");
        } else if (isBullet) {
            sb.append(indent).append("- ").append(text).append("\n");
        } else if (isNumber) {
            sb.append(indent).append("1. ").append(text).append("\n");
        } else {
            sb.append(text).append("\n\n");
        }
    }

    /** 处理表格 */
    private void processTable(XWPFTable table, StringBuilder sb, Map<String, String> imageMapping) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return;

        sb.append("\n");
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText().trim().replace("|", "\\|").replace("\n", " ");
                sb.append(" ").append(cellText).append(" |");
            }
            sb.append("\n");

            // 表头分隔行
            if (i == 0) {
                sb.append("|");
                for (int j = 0; j < row.getTableCells().size(); j++) {
                    sb.append("-----|");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");
    }

    /** 从样式名提取标题级别 */
    private int getHeadingLevel(String style) {
        if (style == null) return 0;
        if (style.startsWith("Heading") || style.startsWith("heading")) {
            try {
                return Integer.parseInt(style.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /** 从 Markdown 内容提取第一个标题 */
    private String extractFirstHeading(String content) {
        for (String line : content.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) return trimmed.substring(2).trim();
            if (trimmed.startsWith("## ")) return trimmed.substring(3).trim();
        }
        return content.length() > MAX_TITLE_LENGTH ? content.substring(0, MAX_TITLE_LENGTH).trim() : content;
    }

    /** 从 Markdown 内容提取首个 # 标题 */
    private String extractMarkdownTitle(String content) {
        return extractFirstHeading(content);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "文件大小不能超过 20MB");
        }
        String ext = getExtension(file.getOriginalFilename());
        if (!WORD_EXTENSIONS.contains(ext) && !MARKDOWN_EXTENSIONS.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持 .doc、.docx、.md 格式的文件");
        }
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }

    private DocumentUploadResponse buildResult(String content, String title, List<String> images) {
        return new DocumentUploadResponse(content, title, images);
    }
}
