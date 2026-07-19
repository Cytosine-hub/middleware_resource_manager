package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.middleware.manager.domain.ForumPost;
import com.middleware.manager.repository.ForumCommentRepository;
import com.middleware.manager.repository.ForumPostRepository;
import com.middleware.manager.repository.ForumTagRepository;
import com.middleware.manager.repository.PostLikeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

/**
 * TC-03 / TC-04：infra 论坛公共模块按岗位（category）筛选及边界处理。
 * 论坛新增 category 字段，指定岗位时走 JPQL 过滤（H2 可测），未指定时保持原有行为。
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ForumCategoryFilterTest {

    @Autowired
    private ForumPostRepository postRepo;
    @Autowired
    private ForumTagRepository tagRepo;
    @Autowired
    private ForumCommentRepository commentRepo;
    @Autowired
    private PostLikeRepository postLikeRepo;

    private ForumService forumService;

    @BeforeEach
    void setUp() {
        forumService = new ForumService(postRepo, tagRepo, commentRepo, postLikeRepo);
    }

    private void post(String title, String category) {
        forumService.createPost(title, "内容-" + title, List.of(), category, "alice", "Alice");
    }

    @Test
    void TC_03_论坛按岗位筛选仅返回对应岗位帖子() {
        post("中间件调优", "中间件");
        post("Nginx实践", "中间件");
        post("MySQL主从", "数据库");

        Page<ForumPost> middleware = forumService.listPosts("", "", "中间件", 0, 12);
        assertThat(middleware.getTotalElements()).isEqualTo(2);
        assertThat(middleware.getContent()).allMatch(p -> "中间件".equals(p.getCategory()));

        // 未指定岗位（全部）返回全部已发布帖子
        assertThat(forumService.listPosts("", "", "", 0, 12).getTotalElements()).isEqualTo(3);
    }

    @Test
    void TC_04_论坛无数据与单条数据岗位的边界处理() {
        post("唯一网络帖", "网络");

        // 无内容岗位：空结果、不报错
        Page<ForumPost> empty = forumService.listPosts("", "", "主机", 0, 12);
        assertThat(empty.getTotalElements()).isZero();
        assertThat(empty.getContent()).isEmpty();

        // 单条数据岗位：正常返回
        assertThat(forumService.listPosts("", "", "网络", 0, 12).getTotalElements()).isEqualTo(1);
    }

    @Test
    void TC_03_论坛岗位内可再按关键字筛选() {
        post("中间件调优指南", "中间件");
        post("中间件部署手册", "中间件");

        assertThat(forumService.listPosts("调优", "", "中间件", 0, 12).getTotalElements()).isEqualTo(1);
    }
}
