package com.middleware.manager.service;

import com.middleware.manager.domain.*;
import com.middleware.manager.repository.*;
import com.middleware.manager.domain.PostLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ForumService {
    private static final Logger log = LoggerFactory.getLogger(ForumService.class);
    private final ForumPostRepository postRepo;
    private final ForumTagRepository tagRepo;
    private final ForumCommentRepository commentRepo;
    private final PostLikeRepository postLikeRepo;

    public ForumService(ForumPostRepository postRepo, ForumTagRepository tagRepo, ForumCommentRepository commentRepo, PostLikeRepository postLikeRepo) {
        this.postRepo = postRepo;
        this.tagRepo = tagRepo;
        this.commentRepo = commentRepo;
        this.postLikeRepo = postLikeRepo;
        try {
            postRepo.addFulltextIndex();
            log.info("Forum FULLTEXT index created");
        } catch (Exception e) {
            log.debug("Forum FULLTEXT index already exists or creation skipped: {}", e.getMessage());
        }
    }

    public Page<ForumPost> listPosts(String keyword, String tag, int page, int size) {
        return listPosts(keyword, tag, null, page, size);
    }

    /**
     * 帖子列表，支持按岗位分类（category）筛选。
     * 指定 category 时走 JPQL 岗位筛选（关键字用 LIKE）；未指定时保持原有全文检索/标签行为不变。
     */
    public Page<ForumPost> listPosts(String keyword, String tag, String category, int page, int size) {
        int s = Math.min(Math.max(size, 1), 50);
        PageRequest pr = PageRequest.of(Math.max(page, 0), s, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (StringUtils.hasText(category)) {
            String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
            String tg = StringUtils.hasText(tag) ? tag.trim() : null;
            return postRepo.findByCategory(category.trim(), kw, tg, pr);
        }
        if (StringUtils.hasText(keyword) || StringUtils.hasText(tag)) {
            String kw = StringUtils.hasText(keyword) ? sanitizeFulltext(keyword.trim()) : null;
            String tg = StringUtils.hasText(tag) ? tag.trim() : null;
            return postRepo.search(kw, tg, pr);
        }
        return postRepo.findByStatusOrderByCreatedAtDesc("PUBLISHED", pr);
    }

    public ForumPost getPost(Long id) {
        return postRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("文章不存在"));
    }

    @Transactional
    public ForumPost createPost(String title, String content, List<String> tagNames,
                                 String authorUsername, String authorDisplayName) {
        return createPost(title, content, tagNames, null, authorUsername, authorDisplayName);
    }

    @Transactional
    public ForumPost createPost(String title, String content, List<String> tagNames, String category,
                                 String authorUsername, String authorDisplayName) {
        ForumPost post = new ForumPost();
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(normalizeCategory(category));
        post.setAuthorUsername(authorUsername);
        post.setAuthorDisplayName(authorDisplayName);
        post.setStatus("PUBLISHED");
        post.setTags(resolveTags(tagNames));
        return postRepo.save(post);
    }

    @Transactional
    public ForumPost updatePost(Long id, String title, String content, List<String> tagNames, String username) {
        return updatePost(id, title, content, tagNames, null, username);
    }

    @Transactional
    public ForumPost updatePost(Long id, String title, String content, List<String> tagNames, String category, String username) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username))
            throw new IllegalArgumentException("只能编辑自己的文章");
        post.setTitle(title);
        post.setContent(content);
        if (StringUtils.hasText(category)) {
            post.setCategory(normalizeCategory(category));
        }
        updateTags(post, tagNames);
        return postRepo.save(post);
    }

    private String normalizeCategory(String category) {
        return StringUtils.hasText(category) ? category.trim() : null;
    }

    @Transactional
    public void deletePost(Long id, String username) {
        ForumPost post = getPost(id);
        if (!post.getAuthorUsername().equals(username))
            throw new IllegalArgumentException("只能删除自己的文章");
        for (ForumTag tag : post.getTags()) {
            tag.setPostCount(Math.max(0, tag.getPostCount() - 1));
            tagRepo.save(tag);
        }
        commentRepo.findByPostIdOrderByCreatedAtAsc(id).forEach(c -> commentRepo.delete(c));
        postRepo.delete(post);
    }

    @Transactional
    public void incrementViewCount(Long id) {
        ForumPost post = getPost(id);
        post.setViewCount(post.getViewCount() + 1);
        postRepo.save(post);
    }

    @Transactional
    public Map<String, Object> toggleLike(Long postId, String username) {
        ForumPost post = getPost(postId);
        boolean alreadyLiked = postLikeRepo.existsByPostIdAndUsername(postId, username);
        Map<String, Object> result = new LinkedHashMap<>();
        if (alreadyLiked) {
            postLikeRepo.deleteByPostIdAndUsername(postId, username);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postRepo.save(post);
            result.put("liked", false);
        } else {
            PostLike pl = new PostLike();
            pl.setPostId(postId);
            pl.setUsername(username);
            postLikeRepo.save(pl);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepo.save(post);
            result.put("liked", true);
        }
        result.put("likeCount", post.getLikeCount());
        return result;
    }

    public boolean hasUserLiked(Long postId, String username) {
        return postLikeRepo.existsByPostIdAndUsername(postId, username);
    }

    // Comments
    public List<ForumComment> getComments(Long postId) {
        return commentRepo.findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public ForumComment addComment(Long postId, String content, String authorUsername, String authorDisplayName) {
        ForumPost post = getPost(postId);
        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setContent(content);
        comment.setAuthorUsername(authorUsername);
        comment.setAuthorDisplayName(authorDisplayName);
        ForumComment saved = commentRepo.save(comment);
        post.setCommentCount(post.getCommentCount() + 1);
        postRepo.save(post);
        return saved;
    }

    @Transactional
    public ForumComment addReply(Long postId, Long parentId, String content, String authorUsername, String authorDisplayName) {
        getPost(postId);
        commentRepo.findById(parentId).orElseThrow(() -> new IllegalArgumentException("父评论不存在"));
        ForumComment reply = new ForumComment();
        reply.setPostId(postId);
        reply.setParentId(parentId);
        reply.setContent(content);
        reply.setAuthorUsername(authorUsername);
        reply.setAuthorDisplayName(authorDisplayName);
        return commentRepo.save(reply);
    }

    @Transactional
    public Map<String, Object> toggleCommentLike(Long commentId) {
        ForumComment comment = commentRepo.findById(commentId).orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        comment.setLikeCount(comment.getLikeCount() + 1);
        commentRepo.save(comment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("likeCount", comment.getLikeCount());
        return result;
    }

    // Tags
    public List<ForumTag> getAllTags() {
        return tagRepo.findAllByOrderByPostCountDesc();
    }

    private Set<ForumTag> resolveTags(List<String> names) {
        if (names == null || names.isEmpty()) return new HashSet<>();
        return names.stream().map(this::getOrCreateTag).collect(Collectors.toSet());
    }

    private void updateTags(ForumPost post, List<String> newNames) {
        Set<String> newSet = newNames != null ? newNames.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet()) : new HashSet<>();
        Set<String> oldSet = post.getTags().stream().map(ForumTag::getName).collect(Collectors.toSet());
        for (ForumTag tag : new HashSet<>(post.getTags())) {
            if (!newSet.contains(tag.getName())) {
                post.getTags().remove(tag);
                tag.setPostCount(Math.max(0, tag.getPostCount() - 1));
                tagRepo.save(tag);
            }
        }
        for (String name : newSet) {
            if (!oldSet.contains(name)) {
                post.getTags().add(getOrCreateTag(name));
            }
        }
    }

    private ForumTag getOrCreateTag(String name) {
        String trimmed = name.trim();
        ForumTag tag = tagRepo.findByNameIgnoreCase(trimmed).orElseGet(() -> tagRepo.save(new ForumTag(trimmed)));
        tag.setPostCount(tag.getPostCount() + 1);
        return tagRepo.save(tag);
    }

    private String sanitizeFulltext(String keyword) {
        return keyword.replaceAll("[+\\-<>()~*\"@%_]", " ").trim().replaceAll("\\s+", " ");
    }
}
