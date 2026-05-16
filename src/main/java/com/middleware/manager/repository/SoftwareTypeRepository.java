package com.middleware.manager.repository;

import com.middleware.manager.domain.SoftwareType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SoftwareTypeRepository extends JpaRepository<SoftwareType, Long> {

    List<SoftwareType> findByActiveTrueOrderByCategoryAscNameAsc();

    List<SoftwareType> findAllByOrderByCategoryAscNameAsc();

    @Query("select distinct type.category from SoftwareType type order by type.category")
    List<String> findDistinctCategories();

    boolean existsByCategoryIgnoreCaseAndNameIgnoreCase(String category, String name);

    Optional<SoftwareType> findByCategoryIgnoreCaseAndNameIgnoreCase(String category, String name);
}
