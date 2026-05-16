package com.middleware.manager.service;

import com.middleware.manager.domain.SoftwareCategory;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.ReleaseAssetRepository;
import com.middleware.manager.repository.SoftwareCategoryRepository;
import com.middleware.manager.repository.SoftwareTypeRepository;
import com.middleware.manager.web.api.dto.SoftwareTypeRequest;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SoftwareTypeService implements ApplicationRunner {
    private final SoftwareTypeRepository softwareTypeRepository;
    private final SoftwareCategoryRepository softwareCategoryRepository;
    private final ReleaseAssetRepository releaseAssetRepository;

    public SoftwareTypeService(SoftwareTypeRepository softwareTypeRepository,
                               SoftwareCategoryRepository softwareCategoryRepository,
                               ReleaseAssetRepository releaseAssetRepository) {
        this.softwareTypeRepository = softwareTypeRepository;
        this.softwareCategoryRepository = softwareCategoryRepository;
        this.releaseAssetRepository = releaseAssetRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCategory("中间件");
        seedCategory("主机");
        seedCategory("数据库");
        seedCategory("安全");
        seedCategory("网络");
        seed("中间件", "Redis");
        seed("中间件", "nginx");
        seed("中间件", "tomcat");
        seed("中间件", "jdk");
        seed("主机", "Linux");
        seed("主机", "Windows Server");
        seed("数据库", "MySQL");
        seed("数据库", "PostgreSQL");
        seed("数据库", "Oracle");
        seed("安全", "Nessus");
        seed("安全", "OpenVAS");
        seed("网络", "F5");
        seed("网络", "HAProxy");
        seed("网络", "Keepalived");
    }

    public List<SoftwareType> list(boolean activeOnly) {
        return activeOnly
                ? softwareTypeRepository.findByActiveTrueOrderByCategoryAscNameAsc()
                : softwareTypeRepository.findAllByOrderByCategoryAscNameAsc();
    }

    public List<String> listCategories() {
        Set<String> names = new LinkedHashSet<>();
        softwareCategoryRepository.findAllByOrderByNameAsc().forEach(category -> names.add(category.getName()));
        names.addAll(softwareTypeRepository.findDistinctCategories().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList()));
        return new ArrayList<>(names);
    }

    @Transactional
    public void createCategory(String category) {
        ensureCategory(normalizeCategory(category));
    }

    public SoftwareType get(Long id) {
        return softwareTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("软件类型不存在"));
    }

    @Transactional
    public SoftwareType create(SoftwareTypeRequest request) {
        String category = normalizeCategory(request.getCategory());
        String name = normalizeName(request.getName());
        if (softwareTypeRepository.existsByCategoryIgnoreCaseAndNameIgnoreCase(category, name)) {
            throw new IllegalArgumentException("同分类下的软件类型已存在");
        }

        SoftwareType type = new SoftwareType();
        ensureCategory(category);
        type.setCategory(category);
        type.setName(name);
        type.setDescription(trimToNull(request.getDescription()));
        type.setActive(request.isActive());
        return softwareTypeRepository.save(type);
    }

    @Transactional
    public SoftwareType update(Long id, SoftwareTypeRequest request) {
        SoftwareType type = get(id);
        String category = normalizeCategory(request.getCategory());
        String name = normalizeName(request.getName());
        softwareTypeRepository.findByCategoryIgnoreCaseAndNameIgnoreCase(category, name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("同分类下的软件类型已存在");
                });

        type.setCategory(category);
        ensureCategory(category);
        type.setName(name);
        type.setDescription(trimToNull(request.getDescription()));
        type.setActive(request.isActive());
        return softwareTypeRepository.save(type);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (releaseAssetRepository.existsBySoftwareTypeId(id)) {
            throw new IllegalStateException("该类型已被资源引用，不能删除，可改为停用");
        }
        softwareTypeRepository.deleteById(id);
    }

    private void seed(String category, String name) {
        seedCategory(category);
        if (softwareTypeRepository.existsByCategoryIgnoreCaseAndNameIgnoreCase(category, name)) {
            return;
        }
        SoftwareType type = new SoftwareType();
        type.setCategory(category);
        type.setName(name);
        type.setActive(true);
        softwareTypeRepository.save(type);
    }

    private void seedCategory(String category) {
        String name = normalizeCategory(category);
        if (softwareCategoryRepository.existsByNameIgnoreCase(name)) {
            return;
        }
        SoftwareCategory softwareCategory = new SoftwareCategory();
        softwareCategory.setName(name);
        softwareCategoryRepository.save(softwareCategory);
    }

    private void ensureCategory(String category) {
        if (softwareCategoryRepository.existsByNameIgnoreCase(category)) {
            return;
        }
        SoftwareCategory softwareCategory = new SoftwareCategory();
        softwareCategory.setName(category);
        softwareCategoryRepository.save(softwareCategory);
    }

    private String normalizeCategory(String category) {
        String value = StringUtils.hasText(category) ? category.trim() : "";
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("类型分类不能为空");
        }
        return value;
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("软件类型名称不能为空");
        }
        return name.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
