# Wiki Ingest Quality Optimization DB Notes

Date: 2026-06-12

## Summary

This change implements the first phase of directory-driven Wiki ingest:

- document type classification
- document outline and section extraction
- section-facts prompt
- page-plan prompt
- planned page generation
- source_refs section coverage
- quality gate status for ingest tasks
- quality report persistence
- canonical title and alias title persistence
- source detail quality visualization

## Schema Changes

The directory-driven ingest quality work adds task-level quality reports and
page-level title matching fields.

Migration scripts:

- `db/wiki_ingest_quality_report_20260612.sql`
- `db/wiki_page_title_match_20260612.sql`

The implementation reuses existing columns:

- `wiki_pages.source_refs`
  - now stores section-level references in JSON:

    ```json
    {
      "source_id": 12,
      "source_title": "example.pdf",
      "source_type": "UPLOAD",
      "sections": [
        {
          "section_id": "sec-001",
          "section_path": "配置/连接池参数",
          "char_range": "1200-2200",
          "page_range": "6-7",
          "paragraph_range": "18-24",
          "source_signal": "numbered-heading"
        }
      ]
    }
    ```

- `wiki_ingest_tasks.status`
  - existing `COMPLETED`, `PARTIAL`, and `FAILED` values are reused for quality gate results.

- `wiki_ingest_tasks.error_message`
  - reused for quality gate summary when status is `PARTIAL` or `FAILED`.

- `wiki_ingest_log.error_detail`
  - reused for quality gate summary.

New column:

- `wiki_ingest_tasks.quality_report`
  - stores the complete quality gate report JSON returned by planned ingest.
  - used by task detail APIs and future frontend quality visualization.

- `wiki_pages.canonical_title`
  - stores a normalized title for stable merge matching.
  - indexed by `idx_canonical_title`.

- `wiki_pages.alias_titles`
  - stores generated equivalent titles as JSON.
  - used together with `canonical_title` when matching existing pages.

## Release Notes

Apply these scripts before releasing the planned ingest quality feature:

1. `db/wiki_ingest_quality_report_20260612.sql`
2. `db/wiki_page_title_match_20260612.sql`

Related operational note:

- `db/wiki_vector_filter_milvus_20260612.md`
