-- Wiki ingest intermediate artifacts persistence
-- Date: 2026-06-12
--
-- Purpose:
-- Persist section_facts and page_plan intermediate artifacts for each wiki
-- ingest task so release, troubleshooting and frontend quality visualization
-- can read them after the async task has finished.

ALTER TABLE wiki_ingest_tasks
    ADD COLUMN section_facts JSON NULL COMMENT '章节事实中间产物' AFTER quality_report,
    ADD COLUMN page_plan JSON NULL COMMENT '页面计划中间产物' AFTER section_facts;
