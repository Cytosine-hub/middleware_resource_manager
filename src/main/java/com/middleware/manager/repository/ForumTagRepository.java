package com.middleware.manager.repository;

import com.middleware.manager.domain.ForumTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ForumTagRepository extends JpaRepository<ForumTag, Long> {
    Optional<ForumTag> findByNameIgnoreCase(String name);
    List<ForumTag> findAllByOrderByPostCountDesc();
}
