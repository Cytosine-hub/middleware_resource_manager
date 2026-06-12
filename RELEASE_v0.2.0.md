# Release v0.2.0 — Wiki Ingest Quality Optimization

**Date**: 2026-06-12
**Author**: zhushihao
**Branch**: feature/ops-agent

## Overview

This release implements directory-driven Wiki ingest with quality gates, intermediate artifact persistence, and comprehensive performance optimizations for large document processing.

## Database Migration Scripts

Apply in order:

1. `db/wiki_ingest_quality_report_20260612.sql` — Add `quality_report` JSON column to `wiki_ingest_tasks`
2. `db/wiki_page_title_match_20260612.sql` — Add `canonical_title` and `alias_titles` columns to `wiki_pages`
3. `db/wiki_ingest_artifacts_20260612.sql` — Add `section_facts` and `page_plan` JSON columns to `wiki_ingest_tasks`

## New Features

### 1. Directory-Driven Wiki Ingest
- Document type classification (PDF, Word, Markdown)
- Document outline extraction with section hierarchy
- Section-facts extraction via LLM
- Page-plan generation with coverage tracking
- Planned page generation with source references
- Quality gate evaluation (SUCCESS / PARTIAL / FAILED)

### 2. Quality Gate
- Coverage ratio calculation (required sections / total sections)
- Threshold: < 70% = FAILED, 70-90% = PARTIAL, ≥ 90% = SUCCESS
- Missing sections tracking
- Short pages, generic titles, over-compressed pages detection
- Quality report persistence in `wiki_ingest_tasks.quality_report`

### 3. Intermediate Artifact Persistence
- `section_facts` JSON stored per task (chapter-level facts)
- `page_plan` JSON stored per task (page generation plan)
- Available via task detail API for debugging and replay

### 4. Page Title Matching
- `canonical_title` for stable merge matching (normalized, lowercased)
- `alias_titles` JSON for equivalent titles
- Indexed for fast lookup during ingest

## Performance Optimizations

### 1. Concurrent Batch Processing
- `section_facts` batches run in parallel (up to LLM concurrency limit)
- `page_generation` batches run in parallel
- Progress reporting from each batch completion (not after all complete)

### 2. LLM Concurrency
- Increased from 3 to 5 concurrent LLM calls
- PooledChatModel with Semaphore-based throttling

### 3. Query Optimization
- `WikiPageMapper`: New `findAllExcludingContent()` and `findBy*ExcludingContent()` methods
- `IngestTaskMapper`: `findAll` and `findByStatus` exclude large JSON columns
- `LinkResolver`: Uses `findAllIdAndTitle()` for link resolution
- Eliminates `Out of sort memory` errors for large documents (5.9MB+)

### 4. Progress Reporting
- `IngestProgressHelper` with `REQUIRES_NEW` transaction propagation
- Progress updates commit independently (visible to frontend in real-time)
- Frontend shows batch progress: "正在生成章节事实 6/36"

## Bug Fixes

- Fixed progress display stuck at 5% during compilation
- Fixed `Out of sort memory` for large document compilation
- Fixed frontend wiki page content not displaying on click
- Fixed deterministic fallback at batch level for invalid LLM responses

## Schema Changes

### `wiki_pages` Table
```sql
-- New columns
canonical_title VARCHAR(200) NULL -- normalized title for merge matching
alias_titles JSON NULL            -- equivalent titles as JSON array

-- New index
INDEX idx_canonical_title (canonical_title)
```

### `wiki_ingest_tasks` Table
```sql
-- New columns
quality_report JSON NULL  -- quality gate report
section_facts JSON NULL   -- intermediate section facts
page_plan JSON NULL       -- intermediate page plan
```

## API Changes

### New Endpoints
None

### Modified Endpoints
- `GET /api/wiki/pages` — Returns pages without `content` column (use `/pages/{id}` for full content)
- `GET /api/wiki/pages/{id}` — Returns full page with content
- `GET /api/wiki/tasks` — Returns tasks without large JSON columns (use `/tasks/{id}` for full details)

## Configuration

### Environment Variables
No new environment variables.

### Application Properties
```yaml
app:
  wiki:
    ingest:
      max-content-chars: 50000
      max-concurrent: 2
    llm:
      max-concurrent: 5
```

## Testing

- 82 unit tests passing
- PDF E2E test verified with 5.9MB TongWeb cluster guide
- Quality gate: 88% coverage, 35 pages created

## Known Issues

- PARTIAL status does not mark source as `ingested=true` (by design)
- No incremental recompile for missing sections (full recompile required)
- `SELECT *` queries with ORDER BY on large tables may still hit sort buffer limits

## Rollback

To rollback this release:
1. Remove new columns:
   ```sql
   ALTER TABLE wiki_ingest_tasks DROP COLUMN section_facts, DROP COLUMN page_plan;
   ALTER TABLE wiki_ingest_tasks DROP COLUMN quality_report;
   ALTER TABLE wiki_pages DROP COLUMN canonical_title, DROP COLUMN alias_titles;
   ALTER TABLE wiki_pages DROP INDEX idx_canonical_title;
   ```
2. Revert code to previous commit

## Related Documents

- `db/wiki_ingest_quality_optimization_20260612.md` — Detailed technical notes
- `db/wiki_vector_filter_milvus_20260612.md` — Milvus vector filter configuration
- `docs/wiki-ingest-quality-optimization-plan.md` — Original optimization plan
- `docs/wiki-ingest-section-facts-optimization.md` — Section facts performance plan
