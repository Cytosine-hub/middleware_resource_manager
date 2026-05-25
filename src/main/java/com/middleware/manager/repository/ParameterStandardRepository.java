package com.middleware.manager.repository;

import com.middleware.manager.domain.ParameterStandard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ParameterStandardRepository extends JpaRepository<ParameterStandard, Long>, JpaSpecificationExecutor<ParameterStandard> {
    Page<ParameterStandard> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<ParameterStandard> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<ParameterStandard> findByStatusInOrderByPublishedAtDesc(List<String> statuses);
}
