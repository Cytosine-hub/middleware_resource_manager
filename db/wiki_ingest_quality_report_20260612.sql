-- Wiki ingest quality report persistence
-- Date: 2026-06-12
--
-- Purpose:
-- Persist the complete quality gate report for each wiki ingest task so release,
-- troubleshooting and frontend quality visualization can read coverage details
-- after the async task has finished.

ALTER TABLE wiki_ingest_tasks
    ADD COLUMN quality_report JSON NULL COMMENT '质量门禁报告' AFTER error_message;
