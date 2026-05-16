package com.middleware.manager.repository;

import com.middleware.manager.domain.StandardParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface StandardParameterRepository extends JpaRepository<StandardParameter, Long>, JpaSpecificationExecutor<StandardParameter> {
    Optional<StandardParameter> findByStandardDocumentIdAndCodeIgnoreCase(Long standardDocumentId, String code);

    List<StandardParameter> findByActiveTrueOrderByCategoryAscCodeAsc();

    List<StandardParameter> findByStandardDocumentIdAndActiveTrueOrderByCategoryAscCodeAsc(Long standardDocumentId);

    boolean existsByStandardDocumentIdAndCodeIgnoreCase(Long standardDocumentId, String code);
}
