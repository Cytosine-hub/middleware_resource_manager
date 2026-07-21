-- 中文分词依赖 MySQL 全局参数 ngram_token_size，默认值为 2。
-- 如需调整粒度，应先修改全局参数并重启 MySQL，再创建或重建此索引。
ALTER TABLE forum_posts
    ADD FULLTEXT INDEX ft_forum_posts_title_content (title, content) WITH PARSER ngram;
