package com.middleware.manager.service;

import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.StandardDocumentRepository;
import com.middleware.manager.repository.StandardParameterRepository;
import com.middleware.manager.web.api.dto.StandardParameterRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class StandardParameterService {
    private final StandardParameterRepository repository;
    private final StandardDocumentRepository standardDocumentRepository;

    public StandardParameterService(StandardParameterRepository repository,
                                    StandardDocumentRepository standardDocumentRepository) {
        this.repository = repository;
        this.standardDocumentRepository = standardDocumentRepository;
    }

    public List<StandardParameter> list(String keyword, String category, Boolean active, Long standardDocumentId) {
        if (standardDocumentId == null) {
            return Collections.emptyList();
        }
        return repository.findAll(specification(keyword, category, active, standardDocumentId));
    }

    public List<StandardParameter> listActive() {
        return repository.findByActiveTrueOrderByCategoryAscCodeAsc();
    }

    public List<StandardParameter> listActiveByStandardDocumentId(Long standardDocumentId) {
        if (standardDocumentId == null) {
            return Collections.emptyList();
        }
        return repository.findByStandardDocumentIdAndActiveTrueOrderByCategoryAscCodeAsc(standardDocumentId);
    }

    public StandardParameter get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("标准参数不存在"));
    }

    @Transactional
    public StandardParameter create(StandardParameterRequest request) {
        Long standardDocumentId = requireStandardDocumentId(request.getStandardDocumentId());
        String code = normalizeCode(request.getCode());
        if (repository.existsByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code)) {
            throw new IllegalArgumentException("该标准下参数编码已存在");
        }

        StandardParameter parameter = new StandardParameter();
        apply(parameter, request, standardDocumentId, code);
        StandardParameter saved = repository.save(parameter);
        clearRenderedContent(standardDocumentId);
        return saved;
    }

    @Transactional
    public StandardParameter update(Long id, StandardParameterRequest request) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = request.getStandardDocumentId() != null
                ? requireStandardDocumentId(request.getStandardDocumentId())
                : requireStandardDocumentId(parameter.getStandardDocumentId());
        String code = normalizeCode(request.getCode());
        repository.findByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("该标准下参数编码已存在");
                });

        apply(parameter, request, standardDocumentId, code);
        StandardParameter saved = repository.save(parameter);
        clearRenderedContent(standardDocumentId);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = parameter.getStandardDocumentId();
        repository.delete(parameter);
        if (standardDocumentId != null) {
            clearRenderedContent(standardDocumentId);
        }
    }

    private void clearRenderedContent(Long standardDocumentId) {
        standardDocumentRepository.findById(standardDocumentId).ifPresent(doc -> {
            doc.setRenderedContent(null);
            standardDocumentRepository.save(doc);
        });
    }

    private void apply(StandardParameter parameter, StandardParameterRequest request, Long standardDocumentId, String code) {
        parameter.setStandardDocumentId(standardDocumentId);
        parameter.setCode(code);
        parameter.setName(requireText(request.getName(), "标准参数名称不能为空"));
        parameter.setValue(requireText(request.getValue(), "标准参数值不能为空"));
        parameter.setCategory(trimToNull(request.getCategory()));
        parameter.setDescription(trimToNull(request.getDescription()));
        parameter.setActive(request.isActive());
    }

    private Specification<StandardParameter> specification(String keyword, String category, Boolean active, Long standardDocumentId) {
        Specification<StandardParameter> specification = Specification.where(null);
        if (standardDocumentId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("standardDocumentId"), standardDocumentId));
        }
        if (StringUtils.hasText(keyword)) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("value")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
            ));
        }
        if (StringUtils.hasText(category)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        }
        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return specification;
    }

    private Long requireStandardDocumentId(Long standardDocumentId) {
        if (standardDocumentId == null) {
            throw new IllegalArgumentException("参数必须绑定标准");
        }
        if (!standardDocumentRepository.existsById(standardDocumentId)) {
            throw new IllegalArgumentException("绑定的标准不存在");
        }
        return standardDocumentId;
    }

    private String normalizeCode(String code) {
        return requireText(code, "标准参数编码不能为空").trim().toUpperCase();
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
