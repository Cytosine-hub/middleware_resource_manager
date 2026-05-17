package com.middleware.manager.repository;

import com.middleware.manager.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndUsername(Long postId, String username);
    void deleteByPostIdAndUsername(Long postId, String username);
}
