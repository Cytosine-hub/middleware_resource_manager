package com.middleware.manager.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardDocument;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.ParameterStandardRepository;
import com.middleware.manager.repository.ReviewRecordRepository;
import com.middleware.manager.repository.StandardDocumentRepository;
import com.middleware.manager.web.api.dto.StandardDocumentRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class StandardDocumentService {
    private final StandardDocumentRepository repository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final StandardParameterService parameterService;
    private final SoftwareTypeService softwareTypeService;
    private final ParameterStandardRepository parameterStandardRepository;

    public StandardDocumentService(StandardDocumentRepository repository,
                                   ReviewRecordRepository reviewRecordRepository,
                                   StandardParameterService parameterService,
                                   SoftwareTypeService softwareTypeService,
                                   ParameterStandardRepository parameterStandardRepository) {
        this.repository = repository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.parameterService = parameterService;
        this.softwareTypeService = softwareTypeService;
        this.parameterStandardRepository = parameterStandardRepository;
    }

    public List<StandardDocument> list(String keyword, String documentType, String status, String category) {
        return repository.findAll(specification(keyword, documentType, status, category));
    }

    public List<StandardDocument> listPublishedStandards() {
        return repository.findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc("STANDARD", "PUBLISHED");
    }

    public List<StandardDocument> listAllPublished() {
        return repository.findByStatusOrderByCategoryAscPublishedAtDesc("PUBLISHED");
    }

    public List<StandardDocument> listAllPublic() {
        List<StandardDocument> docs = repository.findByStatusInOrderByUpdatedAtDesc(
                java.util.Arrays.asList("PUBLISHED", "MODIFYING"));
        for (StandardDocument doc : docs) {
            if ("MODIFYING".equals(doc.getStatus()) && doc.getPreviousContent() != null) {
                doc.setContent(doc.getPreviousContent());
                doc.setRenderedContent(null);
            }
        }
        return docs;
    }

    /** 公开标准文档列表，支持按岗位分类筛选（category 为空表示全部岗位）。 */
    public List<StandardDocument> listPublishedStandards(String category) {
        return filterByCategory(listPublishedStandards(), category);
    }

    /** 全部公开文档列表，支持按岗位分类筛选（category 为空表示全部岗位）。 */
    public List<StandardDocument> listAllPublic(String category) {
        return filterByCategory(listAllPublic(), category);
    }

    private List<StandardDocument> filterByCategory(List<StandardDocument> documents, String category) {
        if (category == null || category.trim().isEmpty()) {
            return documents;
        }
        String target = category.trim();
        return documents.stream()
                .filter(doc -> target.equals(doc.getCategory()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<StandardDocument> listPublishedRelatedDocuments(Long standardDocumentId) {
        return repository.findByRelatedStandardDocumentIdAndStatusOrderByPublishedAtDescUpdatedAtDesc(standardDocumentId, "PUBLISHED");
    }

    public StandardDocument get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    }

    public StandardDocument getPublished(Long id) {
        StandardDocument document = get(id);
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new IllegalArgumentException("文档不存在或未发布");
        }
        return document;
    }

    @Transactional
    public StandardDocument save(StandardDocument document) {
        return repository.save(document);
    }

    @Transactional
    public StandardDocument create(StandardDocumentRequest request) {
        StandardDocument document = new StandardDocument();
        apply(document, request);
        document.setStatus("DRAFT");
        document.setVersion(VersionManager.firstDraftVersion());
        StandardDocument saved = repository.save(document);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public StandardDocument update(Long id, StandardDocumentRequest request) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可编辑");
        }
        String status = document.getStatus();
        if (!"DRAFT".equals(status) && !"MODIFYING".equals(status)) {
            throw new IllegalStateException("当前状态不可编辑");
        }
        apply(document, request);
        if ("DRAFT".equals(status)) {
            document.setVersion(VersionManager.nextDraftVersion(document.getVersion()));
        } else {
            document.setVersion(VersionManager.nextModifyingVersion(document.getVersion()));
        }
        StandardDocument saved = repository.save(document);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public ReviewRecord submitForReview(Long id, String submitter, String submitterDisplayName) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档已有待审核记录");
        }
        String status = document.getStatus();
        if (!"DRAFT".equals(status) && !"MODIFYING".equals(status)) {
            throw new IllegalStateException("只有草稿或修改中的文档可以提交审核");
        }

        // 创建审核记录
        ReviewRecord record = new ReviewRecord();
        record.setDocumentId(document.getId());
        record.setDocumentTitle(document.getTitle());
        record.setDocumentType(document.getDocumentType());
        record.setCategory(document.getCategory());
        record.setSoftware(document.getSoftware());
        record.setDocumentVersion(document.getVersion());
        record.setSubmitterUsername(submitter);
        record.setSubmitterDisplayName(submitterDisplayName);
        record.setStatus("PENDING");
        record.setSubmittedAt(LocalDateTime.now());
        record.setPreviousContent(document.getPreviousContent());
        record.setCurrentContent(document.getContent());
        ReviewRecord saved = reviewRecordRepository.save(record);

        // 文档关联审核记录
        document.setPendingReviewRecordId(saved.getId());
        document.setSubmittedAt(LocalDateTime.now());
        repository.save(document);

        return saved;
    }

    @Transactional
    public StandardDocument startModify(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可操作");
        }
        if (!"PUBLISHED".equals(document.getStatus())) {
            throw new IllegalStateException("只有已发布的文档可以开始修改");
        }
        document.setPreviousContent(document.getContent());
        document.setStatus("MODIFYING");
        document.setVersion(VersionManager.toModifyingVersion(document.getVersion()));
        document.setSubmittedAt(null);
        document.setReviewedAt(null);
        document.setReviewedBy(null);
        document.setReviewComment(null);
        return repository.save(document);
    }

    @Transactional
    public StandardDocument cancelModify(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可操作");
        }
        if (!"MODIFYING".equals(document.getStatus())) {
            throw new IllegalStateException("只有修改中的文档可以取消修改");
        }
        document.setStatus("PUBLISHED");
        document.setVersion(restorePublishedVersion(document.getVersion()));
        if (document.getPreviousContent() != null) {
            document.setContent(document.getPreviousContent());
            document.setRenderedContent(null);
        }
        document.setSubmittedAt(null);
        document.setReviewedAt(null);
        document.setReviewedBy(null);
        document.setReviewComment(null);
        StandardDocument saved = repository.save(document);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        StandardDocument document = get(id);
        if (document.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("文档正在审核中，不可删除");
        }
        if ("PUBLISHED".equals(document.getStatus())) {
            throw new IllegalStateException("已发布的文档不能删除，请先开始修改后再删除");
        }
        repository.delete(document);
    }

    public String render(StandardDocument document) {
        if (document.getRenderedContent() != null) {
            return document.getRenderedContent();
        }
        String rendered = document.getContent();
        Long parameterStandardId = resolveParameterStandardId(document);
        for (StandardParameter parameter : parameterService.listActiveByStandardDocumentId(parameterStandardId)) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        document.setRenderedContent(rendered);
        return rendered;
    }

    @Transactional
    public void refreshRenderedContent(StandardDocument document) {
        String rendered = document.getContent();
        Long parameterStandardId = resolveParameterStandardId(document);
        for (StandardParameter parameter : parameterService.listActiveByStandardDocumentId(parameterStandardId)) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        document.setRenderedContent(rendered);
        repository.save(document);
    }

    @Transactional
    public void refreshRenderedContentForStandard(Long standardDocumentId) {
        StandardDocument standard = get(standardDocumentId);
        refreshRenderedContent(standard);
    }

    private void apply(StandardDocument document, StandardDocumentRequest request) {
        document.setTitle(requireText(request.getTitle(), "文档标题不能为空"));
        String documentType = normalizeDocumentType(request.getDocumentType());
        document.setDocumentType(documentType);
        document.setSummary(trimToNull(request.getSummary()));

        if ("STANDARD".equals(documentType)) {
            com.middleware.manager.domain.SoftwareType softwareType = resolveSoftwareType(request.getSoftwareTypeId());
            document.setRelatedStandardDocumentId(null);
            document.setSoftwareTypeId(softwareType.getId());
            document.setCategory(softwareType.getCategory());
            document.setSoftware(softwareType.getName());
            document.setSoftwareVersion(trimToNull(request.getSoftwareVersion()));
            document.setStandardVersion(trimToNull(request.getStandardVersion()));
        } else {
            Long relatedId = request.getRelatedStandardDocumentId();
            if (relatedId == null) {
                throw new IllegalArgumentException("文档必须关联标准");
            }
            ParameterStandard ps = parameterStandardRepository.findById(relatedId)
                    .orElseThrow(() -> new IllegalArgumentException("关联的参数标准不存在"));
            document.setRelatedStandardDocumentId(relatedId);
            document.setSoftwareTypeId(ps.getSoftwareTypeId());
            document.setCategory(ps.getCategory());
            document.setSoftware(ps.getSoftware());
            document.setSoftwareVersion(ps.getSoftwareVersion());
        }

        document.setContent(requireText(request.getContent(), "文档内容不能为空"));
        document.setCode(trimToNull(request.getCode()));
    }

    private Specification<StandardDocument> specification(String keyword, String documentType, String status, String category) {
        Specification<StandardDocument> specification = Specification.where(null);
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("software"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("softwareVersion"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("standardVersion"), "")), pattern),
                    cb.like(cb.lower(root.get("content")), pattern)
            ));
        }
        if (StringUtils.hasText(documentType)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("documentType"), documentType.trim()));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status.trim()));
        }
        if (StringUtils.hasText(category)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        }
        return specification;
    }

    private String normalizeDocumentType(String documentType) {
        String value = StringUtils.hasText(documentType) ? documentType.trim().toUpperCase() : "MANUAL";
        if (!"MANUAL".equals(value) && !"ARTICLE".equals(value)) {
            throw new IllegalArgumentException("文档类型必须是 MANUAL 或 ARTICLE");
        }
        return value;
    }

    private com.middleware.manager.domain.SoftwareType resolveSoftwareType(Long softwareTypeId) {
        if (softwareTypeId == null) {
            throw new IllegalArgumentException("标准必须选择软件类型");
        }
        return softwareTypeService.get(softwareTypeId);
    }

    private Long resolveParameterStandardId(StandardDocument document) {
        if ("STANDARD".equals(document.getDocumentType())) {
            return document.getId();
        }
        return document.getRelatedStandardDocumentId();
    }



    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    /** 从修改态版本号还原为已发布版本号：X.YZ → X.Y（去掉末尾的 Z） */
    private String restorePublishedVersion(String modifyingVersion) {
        if (modifyingVersion == null) return VersionManager.firstPublishVersion();
        int dotIdx = modifyingVersion.indexOf('.');
        if (dotIdx < 0) return VersionManager.firstPublishVersion();
        String yzPart = modifyingVersion.substring(dotIdx + 1);
        if (yzPart.length() <= 1) return modifyingVersion; // 已经是 X.Y 格式
        return modifyingVersion.substring(0, dotIdx + 1) + yzPart.substring(0, yzPart.length() - 1);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
