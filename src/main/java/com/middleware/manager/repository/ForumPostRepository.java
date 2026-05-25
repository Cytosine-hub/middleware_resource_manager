package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    Page<ForumPost> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query("SELECT DISTINCT p FROM ForumPost p LEFT JOIN p.tags t WHERE p.status = 'PUBLISHED' AND " +
           "(:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%',:keyword,'%'))) AND " +
           "(:tag IS NULL OR LOWER(t.name) = LOWER(:tag)) ORDER BY p.createdAt DESC")
    Page<ForumPost> search(String keyword, String tag, Pageable pageable);
}
