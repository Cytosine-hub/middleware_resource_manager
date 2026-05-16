package com.middleware.manager.repository;

import com.middleware.manager.domain.SoftwareCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoftwareCategoryRepository extends JpaRepository<SoftwareCategory, Long> {
    List<SoftwareCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
