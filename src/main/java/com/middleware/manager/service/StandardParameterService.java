package com.middleware.manager.service;

import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.repository.ParameterStandardRepository;
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
    private final ParameterStandardRepository parameterStandardRepository;

    public StandardParameterService(StandardParameterRepository repository,
                                    StandardDocumentRepository standardDocumentRepository,
                                    ParameterStandardRepository parameterStandardRepository) {
        this.repository = repository;
        this.standardDocumentRepository = standardDocumentRepository;
        this.parameterStandardRepository = parameterStandardRepository;
    }

    public List<StandardParameter> list(String keyword, String category, Boolean active,
                                        Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId == null && parameterStandardId == null) {
            return Collections.emptyList();
        }
        return repository.findAll(specification(keyword, category, active, standardDocumentId, parameterStandardId));
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

    public List<StandardParameter> listActiveByParameterStandardId(Long parameterStandardId) {
        if (parameterStandardId == null) {
            return Collections.emptyList();
        }
        return repository.findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(parameterStandardId);
    }

    public StandardParameter get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("标准参数不存在"));
    }

    @Transactional
    public StandardParameter create(StandardParameterRequest request) {
        Long standardDocumentId = request.getStandardDocumentId();
        Long parameterStandardId = request.getParameterStandardId();
        resolveBinding(standardDocumentId, parameterStandardId);

        String code = normalizeCode(request.getCode());
        if (standardDocumentId != null) {
            if (repository.existsByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code)) {
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        } else {
            if (repository.existsByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code)) {
                throw new IllegalArgumentException("该标准下参数编码已存在");
            }
        }

        StandardParameter parameter = new StandardParameter();
        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        StandardParameter saved = repository.save(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        return saved;
    }

    @Transactional
    public StandardParameter update(Long id, StandardParameterRequest request) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = request.getStandardDocumentId() != null
                ? request.getStandardDocumentId()
                : parameter.getStandardDocumentId();
        Long parameterStandardId = request.getParameterStandardId() != null
                ? request.getParameterStandardId()
                : parameter.getParameterStandardId();
        resolveBinding(standardDocumentId, parameterStandardId);

        String code = normalizeCode(request.getCode());
        if (standardDocumentId != null) {
            repository.findByStandardDocumentIdAndCodeIgnoreCase(standardDocumentId, code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> { throw new IllegalArgumentException("该标准下参数编码已存在"); });
        } else {
            repository.findByParameterStandardIdAndCodeIgnoreCase(parameterStandardId, code)
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> { throw new IllegalArgumentException("该标准下参数编码已存在"); });
        }

        apply(parameter, request, standardDocumentId, parameterStandardId, code);
        StandardParameter saved = repository.save(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        StandardParameter parameter = get(id);
        Long standardDocumentId = parameter.getStandardDocumentId();
        Long parameterStandardId = parameter.getParameterStandardId();
        repository.delete(parameter);
        clearRenderedContent(standardDocumentId, parameterStandardId);
    }

    private void clearRenderedContent(Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId != null) {
            standardDocumentRepository.findById(standardDocumentId).ifPresent(doc -> {
                doc.setRenderedContent(null);
                standardDocumentRepository.save(doc);
            });
        }
        if (parameterStandardId != null) {
            parameterStandardRepository.findById(parameterStandardId).ifPresent(ps -> {
                ps.setRenderedContent(null);
                parameterStandardRepository.save(ps);
            });
        }
    }

    private void apply(StandardParameter parameter, StandardParameterRequest request,
                       Long standardDocumentId, Long parameterStandardId, String code) {
        parameter.setStandardDocumentId(standardDocumentId);
        parameter.setParameterStandardId(parameterStandardId);
        parameter.setCode(code);
        parameter.setName(requireText(request.getName(), "标准参数名称不能为空"));
        parameter.setValue(requireText(request.getValue(), "标准参数值不能为空"));
        parameter.setCategory(trimToNull(request.getCategory()));
        parameter.setDescription(trimToNull(request.getDescription()));
        parameter.setActive(request.isActive());
        parameter.setDeploymentStandard(request.isDeploymentStandard());
    }

    private void resolveBinding(Long standardDocumentId, Long parameterStandardId) {
        if (standardDocumentId == null && parameterStandardId == null) {
            throw new IllegalArgumentException("参数必须绑定标准");
        }
        if (standardDocumentId != null && parameterStandardId != null) {
            throw new IllegalArgumentException("参数只能绑定一种标准类型");
        }
        if (standardDocumentId != null && !standardDocumentRepository.existsById(standardDocumentId)) {
            throw new IllegalArgumentException("绑定的标准不存在");
        }
        if (parameterStandardId != null && !parameterStandardRepository.existsById(parameterStandardId)) {
            throw new IllegalArgumentException("绑定的参数标准不存在");
        }
    }

    private Specification<StandardParameter> specification(String keyword, String category, Boolean active,
                                                           Long standardDocumentId, Long parameterStandardId) {
        Specification<StandardParameter> specification = Specification.where(null);
        if (standardDocumentId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("standardDocumentId"), standardDocumentId));
        }
        if (parameterStandardId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("parameterStandardId"), parameterStandardId));
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
