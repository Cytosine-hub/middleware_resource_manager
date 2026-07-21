package com.middleware.manager.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForumPostFulltextMigrationTest {

    @Test
    @DisplayName("TC-COMMUNITY-008 论坛迁移为标题和正文创建 ngram FULLTEXT 索引")
    void migrationCreatesNgramFulltextIndex() throws IOException {
        String resourcePath = "db/migration/V20260721__add_forum_post_fulltext_index.sql";

        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(input).as("迁移脚本应存在").isNotNull();
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(migration).containsIgnoringCase("FULLTEXT")
                    .contains("forum_posts")
                    .contains("title, content")
                    .containsIgnoringCase("WITH PARSER ngram")
                    .contains("ngram_token_size")
                    .contains("默认值为 2");
        }
    }
}
