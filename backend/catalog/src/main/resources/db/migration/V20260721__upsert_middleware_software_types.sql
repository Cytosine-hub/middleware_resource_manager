-- 先执行本脚本，再执行 job-middleware 中同日期的命令关联迁移。
-- 全部匹配均按名且忽略大小写，不覆盖 catalog 已有记录。

INSERT INTO software_categories (name, created_at, updated_at)
SELECT '中间件', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM software_categories WHERE LOWER(TRIM(name)) = LOWER('中间件')
);

INSERT INTO software_types (category, name, description, active, created_at, updated_at)
SELECT '中间件', seeds.name, NULL, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'Redis' AS name
    UNION ALL SELECT 'Kafka'
    UNION ALL SELECT 'Zookeeper'
    UNION ALL SELECT 'RabbitMQ'
    UNION ALL SELECT 'RocketMQ'
    UNION ALL SELECT 'Java容器'
    UNION ALL SELECT 'Nacos'
) AS seeds
WHERE NOT EXISTS (
    SELECT 1
    FROM software_types existing
    WHERE LOWER(TRIM(existing.category)) = LOWER('中间件')
      AND LOWER(TRIM(existing.name)) = LOWER(seeds.name)
);
