package com.middleware.manager.service;

import com.middleware.manager.domain.ParameterStandard;
import com.middleware.manager.domain.ReviewRecord;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.ParameterStandardRepository;
import com.middleware.manager.repository.ReviewRecordRepository;
import com.middleware.manager.repository.StandardParameterRepository;
import com.middleware.manager.web.api.dto.ParameterStandardRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class ParameterStandardService {
    private final ParameterStandardRepository repository;
    private final StandardParameterRepository standardParameterRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final SoftwareTypeService softwareTypeService;

    public ParameterStandardService(ParameterStandardRepository repository,
                                    StandardParameterRepository standardParameterRepository,
                                    ReviewRecordRepository reviewRecordRepository,
                                    SoftwareTypeService softwareTypeService) {
        this.repository = repository;
        this.standardParameterRepository = standardParameterRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.softwareTypeService = softwareTypeService;
    }

    public Page<ParameterStandard> list(String keyword, String status, String category, Pageable pageable) {
        Specification<ParameterStandard> spec = specification(keyword, status, category);
        return repository.findAll(spec, pageable);
    }

    public Page<ParameterStandard> listPublished(Pageable pageable) {
        return repository.findByStatusOrderByCreatedAtDesc("PUBLISHED", pageable);
    }

    public List<ParameterStandard> listPublicStandards() {
        List<ParameterStandard> standards = repository.findByStatusInOrderByPublishedAtDesc(
                Arrays.asList("PUBLISHED", "MODIFYING"));
        for (ParameterStandard ps : standards) {
            if ("MODIFYING".equals(ps.getStatus()) && ps.getPreviousContent() != null) {
                ps.setContent(ps.getPreviousContent());
                ps.setRenderedContent(null);
            }
        }
        return standards;
    }

    /** 公开参数标准列表，支持按岗位分类筛选（category 为空表示全部岗位）。 */
    public List<ParameterStandard> listPublicStandards(String category) {
        List<ParameterStandard> standards = listPublicStandards();
        if (category == null || category.trim().isEmpty()) {
            return standards;
        }
        String target = category.trim();
        return standards.stream()
                .filter(s -> target.equals(s.getCategory()))
                .collect(java.util.stream.Collectors.toList());
    }

    public ParameterStandard get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("参数标准不存在"));
    }

    @Transactional
    public ParameterStandard create(ParameterStandardRequest request) {
        ParameterStandard standard = new ParameterStandard();
        apply(standard, request);
        standard.setStatus("DRAFT");
        standard.setVersion(VersionManager.firstDraftVersion());
        ParameterStandard saved = repository.save(standard);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public ParameterStandard update(Long id, ParameterStandardRequest request) {
        ParameterStandard standard = get(id);
        if (!"DRAFT".equals(standard.getStatus()) && !"MODIFYING".equals(standard.getStatus())) {
            throw new IllegalStateException("当前状态不可编辑");
        }
        apply(standard, request);
        if ("DRAFT".equals(standard.getStatus())) {
            standard.setVersion(VersionManager.nextDraftVersion(standard.getVersion()));
        } else {
            standard.setVersion(VersionManager.nextModifyingVersion(standard.getVersion()));
        }
        ParameterStandard saved = repository.save(standard);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ParameterStandard standard = get(id);
        if ("PUBLISHED".equals(standard.getStatus())) {
            throw new IllegalStateException("已发布的参数标准不能删除");
        }
        repository.delete(standard);
    }

    @Transactional
    public ParameterStandard startModify(Long id) {
        ParameterStandard standard = get(id);
        if (!"PUBLISHED".equals(standard.getStatus())) {
            throw new IllegalStateException("只有已发布的参数标准才能开始修改");
        }
        standard.setPreviousContent(standard.getContent());
        standard.setPreviousRenderedContent(buildReviewContent(standard));
        standard.setStatus("MODIFYING");
        standard.setVersion(VersionManager.toModifyingVersion(standard.getVersion()));
        return repository.save(standard);
    }

    @Transactional
    public ParameterStandard cancelModify(Long id) {
        ParameterStandard standard = get(id);
        if (!"MODIFYING".equals(standard.getStatus())) {
            throw new IllegalStateException("当前状态不可取消修改");
        }
        standard.setStatus("PUBLISHED");
        standard.setVersion(restorePublishedVersion(standard.getVersion()));
        if (standard.getPreviousContent() != null) {
            standard.setContent(standard.getPreviousContent());
            standard.setRenderedContent(null);
        }
        standard.setPreviousRenderedContent(null);
        ParameterStandard saved = repository.save(standard);
        refreshRenderedContent(saved);
        return saved;
    }

    @Transactional
    public ParameterStandard publish(Long id) {
        ParameterStandard standard = get(id);
        if (!"DRAFT".equals(standard.getStatus()) && !"MODIFYING".equals(standard.getStatus())) {
            throw new IllegalStateException("当前状态不可发布");
        }
        if ("DRAFT".equals(standard.getStatus())) {
            standard.setVersion(VersionManager.firstPublishVersion());
        } else {
            standard.setVersion(restorePublishedVersion(standard.getVersion()));
        }
        standard.setStatus("PUBLISHED");
        standard.setPublishedAt(LocalDateTime.now());
        standard.setPreviousContent(null);
        standard.setPreviousRenderedContent(null);
        return repository.save(standard);
    }

    @Transactional
    public ParameterStandard submitForReview(Long id, String submitterUsername, String submitterDisplayName) {
        ParameterStandard standard = get(id);
        if (!"DRAFT".equals(standard.getStatus()) && !"MODIFYING".equals(standard.getStatus())) {
            throw new IllegalStateException("当前状态不可提交审核");
        }
        if (standard.getPendingReviewRecordId() != null) {
            throw new IllegalStateException("该标准已在审核中");
        }

        ReviewRecord record = new ReviewRecord();
        record.setDocumentId(standard.getId());
        record.setDocumentTitle(standard.getTitle());
        record.setDocumentType("PARAMETER_STANDARD");
        record.setCategory(standard.getCategory());
        record.setSoftware(standard.getSoftware());
        record.setDocumentVersion(standard.getVersion());
        record.setSubmitterUsername(submitterUsername);
        record.setSubmitterDisplayName(submitterDisplayName);
        record.setStatus("PENDING");
        record.setCurrentContent(buildReviewContent(standard));
        if ("MODIFYING".equals(standard.getStatus()) && standard.getPreviousRenderedContent() != null) {
            record.setPreviousContent(standard.getPreviousRenderedContent());
        }
        record = reviewRecordRepository.save(record);

        standard.setPendingReviewRecordId(record.getId());
        return repository.save(standard);
    }

    public ParameterStandard save(ParameterStandard standard) {
        return repository.save(standard);
    }

    public String render(ParameterStandard standard) {
        if (standard.getRenderedContent() != null) {
            return standard.getRenderedContent();
        }
        String rendered = standard.getContent();
        for (StandardParameter parameter : standardParameterRepository
                .findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(standard.getId())) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        standard.setRenderedContent(rendered);
        return rendered;
    }

    private String buildReviewContent(ParameterStandard standard) {
        StringBuilder sb = new StringBuilder();
        sb.append(render(standard));
        java.util.List<StandardParameter> params = standardParameterRepository
                .findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(standard.getId());
        if (!params.isEmpty()) {
            sb.append("\n\n---\n## 标准参数\n\n");
            sb.append("| 参数编码 | 参数值 | 分类 |\n|---|---|---|\n");
            for (StandardParameter p : params) {
                sb.append("| ").append(p.getCode())
                  .append(" | ").append(p.getValue())
                  .append(" | ").append(p.getCategory() != null ? p.getCategory() : "-")
                  .append(" |\n");
            }
        }
        return sb.toString();
    }

    @Transactional
    public void refreshRenderedContent(ParameterStandard standard) {
        String rendered = standard.getContent();
        for (StandardParameter parameter : standardParameterRepository
                .findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(standard.getId())) {
            rendered = rendered.replace("{{" + parameter.getCode() + "}}", parameter.getValue());
        }
        standard.setRenderedContent(rendered);
        repository.save(standard);
    }

    private void apply(ParameterStandard standard, ParameterStandardRequest request) {
        if (request.getSoftwareTypeId() != null) {
            com.middleware.manager.domain.SoftwareType softwareType = softwareTypeService.get(request.getSoftwareTypeId());
            standard.setSoftwareTypeId(softwareType.getId());
            standard.setCategory(softwareType.getCategory());
            standard.setSoftware(softwareType.getName());
        }
        standard.setSoftwareVersion(trimToNull(request.getSoftwareVersion()));
        standard.setTitle(java.util.stream.Stream.of(
                standard.getCategory(), standard.getSoftware(), standard.getSoftwareVersion())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / ")));
        standard.setCode(trimToNull(request.getCode()));
        standard.setContent(requireText(request.getContent(), "标准内容不能为空"));
    }

    private Specification<ParameterStandard> specification(String keyword, String status, String category) {
        Specification<ParameterStandard> specification = Specification.where(null);
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("category"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("software"), "")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("softwareVersion"), "")), pattern),
                    cb.like(cb.lower(root.get("content")), pattern)
            ));
        }
        if (StringUtils.hasText(status)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status.trim()));
        }
        if (StringUtils.hasText(category)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        }
        return specification;
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
        if (yzPart.length() <= 1) return modifyingVersion;
        return modifyingVersion.substring(0, dotIdx + 1) + yzPart.substring(0, yzPart.length() - 1);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
