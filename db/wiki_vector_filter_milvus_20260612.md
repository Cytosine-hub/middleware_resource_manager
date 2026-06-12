# Wiki Vector Filter Milvus Notes

Date: 2026-06-12

## Summary

Wiki and knowledge search now support metadata filtering through
`VectorSearchFilter`.

The Milvus vector collection remains a single logical collection. New
collections created by the application include scalar fields for filtering:

- `source`
- `category`
- `software`
- `source_type`
- `source_id`
- `status`

The original JSON `metadata` field is still written for compatibility and
fallback filtering.

## Release Notes

No MySQL DDL is required for this change.

Existing Milvus collections created before this change do not have the scalar
fields above. The application can fall back to legacy metadata-only writes and
Java-side metadata filtering, but expression filtering requires recreating or
rebuilding the Milvus collection with the new schema.

Recommended release step when enabling Milvus scalar filtering:

1. Stop backend writers.
2. Back up or confirm MySQL `knowledge_chunks` and `wiki_pages` are intact.
3. Drop/recreate the Milvus collection, or create a new collection name via
   `VECTOR_COLLECTION`.
4. Restart backend and run Wiki page reindex so vectors are rewritten with
   scalar metadata.
