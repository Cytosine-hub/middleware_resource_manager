package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    Page<ForumPost> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query(value = "SELECT DISTINCT p.* FROM forum_posts p " +
           "LEFT JOIN forum_post_tags fpt ON p.id = fpt.post_id " +
           "LEFT JOIN forum_tags t ON fpt.tags_id = t.id " +
           "WHERE p.status = 'PUBLISHED' " +
           "AND (:keyword IS NULL OR MATCH(p.title, p.content) AGAINST(:keyword IN BOOLEAN MODE)) " +
           "AND (:tag IS NULL OR LOWER(t.name) = LOWER(:tag)) " +
           "ORDER BY p.created_at DESC",
           countQuery = "SELECT COUNT(DISTINCT p.id) FROM forum_posts p " +
           "LEFT JOIN forum_post_tags fpt ON p.id = fpt.post_id " +
           "LEFT JOIN forum_tags t ON fpt.tags_id = t.id " +
           "WHERE p.status = 'PUBLISHED' " +
           "AND (:keyword IS NULL OR MATCH(p.title, p.content) AGAINST(:keyword IN BOOLEAN MODE)) " +
           "AND (:tag IS NULL OR LOWER(t.name) = LOWER(:tag))",
           nativeQuery = true)
    Page<ForumPost> search(@Param("keyword") String keyword, @Param("tag") String tag, Pageable pageable);

    @Modifying
    @Query(value = "ALTER TABLE forum_posts ADD FULLTEXT INDEX ft_forum_posts_title_content (title, content)", nativeQuery = true)
    void addFulltextIndex();
}
