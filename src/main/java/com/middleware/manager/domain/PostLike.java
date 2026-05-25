package com.middleware.manager.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "forum_post_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "username"}))
public class PostLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false, length = 60)
    private String username;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
