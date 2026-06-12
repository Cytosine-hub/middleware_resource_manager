-- Wiki page title matching support
-- Date: 2026-06-12
-- Purpose: persist normalized titles and alias titles for stable ingest merge matching.

ALTER TABLE wiki_pages
    ADD COLUMN canonical_title VARCHAR(200) NULL COMMENT 'normalized title used by ingest merge matching' AFTER version,
    ADD COLUMN alias_titles JSON NULL COMMENT 'alias titles emitted by page planner/generator' AFTER canonical_title,
    ADD INDEX idx_canonical_title (canonical_title);

UPDATE wiki_pages
SET canonical_title = LOWER(
    REGEXP_REPLACE(
        title,
        '[[:space:][:punct:]，。；：、（）【】《》“”‘’]+',
        ''
    )
)
WHERE canonical_title IS NULL;
