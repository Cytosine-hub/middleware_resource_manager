package com.middleware.manager.repository;

import com.middleware.manager.domain.StandardDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface StandardDocumentRepository extends JpaRepository<StandardDocument, Long>, JpaSpecificationExecutor<StandardDocument> {
    List<StandardDocument> findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc(String documentType, String status);
    List<StandardDocument> findByRelatedStandardDocumentIdAndStatusOrderByPublishedAtDescUpdatedAtDesc(Long relatedStandardDocumentId, String status);
    List<StandardDocument> findByStatusOrderByCategoryAscPublishedAtDesc(String status);
}
