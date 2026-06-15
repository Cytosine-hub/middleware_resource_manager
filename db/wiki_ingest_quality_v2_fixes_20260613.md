# Wiki Ingest Quality V2 Fixes - 2026-06-13

## Summary

This release note records runtime and schema impact for the V2 wiki ingest quality fixes.

## Schema Changes

- `db/add_paused_status_20260613.sql` adds `PAUSED` to `wiki_ingest_tasks.status`.
- No new table is required for source-document vectorization in this iteration.

## Runtime/Data Behavior Changes

- Wiki page vectors are no longer used as the primary vector index.
- Wiki source documents are vectorized into the existing vector store using deterministic IDs:
  - `wiki_source_{sourceId}_{chunkIndex}`
- Source vector metadata includes:
  - `source=wiki_source`
  - `sourceType`
  - `sourceId`
  - `sourceTitle`
  - `chunkIndex`
  - `content`
  - `sectionId`
  - `sectionPath`
  - `category`
  - `software`
  - `status`
- Legacy wiki page vectors use IDs:
  - `wiki_{pageId}`
  These are no longer upserted. Page delete still attempts to remove the legacy vector ID for cleanup.

## Release Notes

- Run `db/add_paused_status_20260613.sql` before enabling pause/resume task controls.
- Rebuild wiki vectors after deployment through the existing wiki reindex endpoint so existing sources get `wiki_source_*` vectors.
