package com.middleware.manager.repository;

import com.middleware.manager.domain.ReviewRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRecordRepository extends JpaRepository<ReviewRecord, Long> {
    List<ReviewRecord> findBySubmitterUsernameOrderBySubmittedAtDesc(String username);
    List<ReviewRecord> findAllByOrderBySubmittedAtDesc();
    List<ReviewRecord> findByDocumentIdAndStatus(Long documentId, String status);
}
