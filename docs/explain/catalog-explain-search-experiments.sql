-- Isolates the cost of ranked full-text search at 50,000 products ("chelsea boot" matches ~1,460 rows).
-- Only the inner page query is measured; the join to categories and the lateral lookup for 24 rows are cheap.
--   scripts/explain-catalog.sh docs/explain/catalog-explain-search-experiments.sql
\pset pager off

\echo '=== S1: current: ts_rank_cd ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id FROM products p
WHERE p.active AND p.min_price_minor IS NOT NULL
  AND p.search_vector @@ websearch_to_tsquery('english', 'chelsea boot')
ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('english', 'chelsea boot')) DESC, p.id
LIMIT 24;

\echo '=== S2: ts_rank (no cover density) ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id FROM products p
WHERE p.active AND p.min_price_minor IS NOT NULL
  AND p.search_vector @@ websearch_to_tsquery('english', 'chelsea boot')
ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('english', 'chelsea boot')) DESC, p.id
LIMIT 24;

\echo '=== S3: no ranking at all (cost of the match itself) ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id FROM products p
WHERE p.active AND p.min_price_minor IS NOT NULL
  AND p.search_vector @@ websearch_to_tsquery('english', 'chelsea boot')
ORDER BY p.id
LIMIT 24;

\echo '=== S4: a rarer query ("kestrel carbon") with ts_rank_cd ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id FROM products p
WHERE p.active AND p.min_price_minor IS NOT NULL
  AND p.search_vector @@ websearch_to_tsquery('english', 'kestrel carbon')
ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('english', 'kestrel carbon')) DESC, p.id
LIMIT 24;
