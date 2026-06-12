-- Migration: v1.3.0-20260612
-- Date: 2026-06-12
-- Description: Wiki ingest quality optimization + API audit log table

-- ============================================================
-- 1. wiki_ingest_tasks: 新增列
-- ============================================================

-- quality_report 列（如已存在可安全忽略 Duplicate column 错误）
ALTER TABLE wiki_ingest_tasks
    ADD COLUMN quality_report JSON NULL COMMENT '质量门禁报告' AFTER error_message;

-- section_facts 和 page_plan 列（如已存在可安全忽略 Duplicate column 错误）
ALTER TABLE wiki_ingest_tasks
    ADD COLUMN section_facts JSON NULL COMMENT '章节事实中间产物' AFTER quality_report,
    ADD COLUMN page_plan JSON NULL COMMENT '页面计划中间产物' AFTER section_facts;

-- ============================================================
-- 2. wiki_pages: 新增列和索引
-- ============================================================

-- canonical_title 和 alias_titles 列（如已存在可安全忽略 Duplicate column 错误）
ALTER TABLE wiki_pages
    ADD COLUMN canonical_title VARCHAR(200) NULL COMMENT 'normalized title used by ingest merge matching' AFTER version,
    ADD COLUMN alias_titles JSON NULL COMMENT 'alias titles emitted by page planner/generator' AFTER canonical_title;

-- idx_canonical_title 索引（如已存在可安全忽略 Duplicate key name 错误）
ALTER TABLE wiki_pages
    ADD INDEX idx_canonical_title (canonical_title);

-- 回填现有页面的 canonical_title
UPDATE wiki_pages
SET canonical_title = LOWER(
    REGEXP_REPLACE(
        title,
        '[[:space:][:punct:]，。；：、（）【】《》""'']+',
        ''
    )
)
WHERE canonical_title IS NULL;

-- ============================================================
-- 3. api_audit_log: 新增表
-- ============================================================

CREATE TABLE IF NOT EXISTS `api_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(60) NOT NULL COMMENT '用户账号',
  `method` varchar(10) NOT NULL COMMENT 'HTTP方法',
  `path` varchar(500) NOT NULL COMMENT '请求路径',
  `query_string` varchar(500) DEFAULT NULL COMMENT '查询参数',
  `status_code` int DEFAULT NULL COMMENT '响应状态码',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
  `duration_ms` bigint DEFAULT NULL COMMENT '请求耗时(毫秒)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_path` (`path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='API操作审计日志';
