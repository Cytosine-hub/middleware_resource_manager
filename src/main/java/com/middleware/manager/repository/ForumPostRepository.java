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

    /**
     * 按岗位分类筛选帖子，并与标签、标题/内容关键字条件组合（JPQL，非全文索引），供论坛左侧岗位导航使用。
     * <p>保持原论坛「标签 + 标题或内容搜索」的交互：keyword 同时匹配标题与内容，tag 精确匹配标签名；
     * 三类条件均可为空（为空即不约束），共同以 AND 组合，只是在岗位（category）范围内检索。
     */
    @Query(value = "SELECT DISTINCT p FROM ForumPost p LEFT JOIN p.tags t "
            + "WHERE p.status = 'PUBLISHED' AND p.category = :category "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:tag IS NULL OR LOWER(t.name) = LOWER(:tag)) "
            + "ORDER BY p.createdAt DESC",
            countQuery = "SELECT COUNT(DISTINCT p) FROM ForumPost p LEFT JOIN p.tags t "
            + "WHERE p.status = 'PUBLISHED' AND p.category = :category "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "AND (:tag IS NULL OR LOWER(t.name) = LOWER(:tag))")
    Page<ForumPost> findByCategory(@Param("category") String category,
                                   @Param("keyword") String keyword,
                                   @Param("tag") String tag,
                                   Pageable pageable);
}
