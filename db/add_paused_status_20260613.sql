-- Add PAUSED status to wiki_ingest_tasks
-- Date: 2026-06-13

ALTER TABLE wiki_ingest_tasks
    MODIFY COLUMN status ENUM('PENDING','PROCESSING','PAUSED','COMPLETED','PARTIAL','FAILED') DEFAULT 'PENDING';
