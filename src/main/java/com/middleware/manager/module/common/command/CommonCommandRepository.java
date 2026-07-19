package com.middleware.manager.module.common.command;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommonCommandRepository extends JpaRepository<CommonCommand, Long> {

    @Query("SELECT c FROM CommonCommand c "
            + "WHERE (:category IS NULL OR c.category = :category) "
            + "AND (:keyword IS NULL "
            + "     OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(c.command) LIKE LOWER(CONCAT('%', :keyword, '%')) "
            + "     OR LOWER(COALESCE(c.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY c.updatedAt DESC")
    Page<CommonCommand> filter(@Param("category") String category,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
