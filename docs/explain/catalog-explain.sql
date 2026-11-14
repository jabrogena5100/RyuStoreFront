-- Query plans for the main catalog queries (current shape, after V7). Run against a seeded database:
--   scripts/explain-catalog.sh
-- The statements mirror ProductSearchRepository (literal values instead of bind parameters).
-- The original shape (before V7) is kept in catalog-explain-before.sql for comparison.
\pset pager off
\echo '=== table sizes ==='
SELECT (SELECT count(*) FROM products) AS products, (SELECT count(*) FROM variants) AS variants;

-- VACUUM sets the visibility map so index-only scans do not fall back to the heap; ANALYZE refreshes statistics.
VACUUM (ANALYZE);

\echo '=== Q1: default listing, newest first, page 1 ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT 24 OFFSET 0) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.created_at DESC, t.id DESC;

\echo '=== Q2: full-text search "chelsea boot", ranked ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at,
             ts_rank(p.search_vector, websearch_to_tsquery('english', 'chelsea boot')) AS relevance
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
        AND p.search_vector @@ websearch_to_tsquery('english', 'chelsea boot')
      ORDER BY ts_rank(p.search_vector, websearch_to_tsquery('english', 'chelsea boot')) DESC, p.id
      LIMIT 24 OFFSET 0) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.relevance DESC, t.id;

\echo '=== Q3: category boots, price ascending ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
        AND p.category_id IN (SELECT id FROM categories
                              WHERE slug = 'boots' OR parent_id = (SELECT id FROM categories WHERE slug = 'boots'))
      ORDER BY p.min_price_minor ASC, p.id
      LIMIT 24 OFFSET 0) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.min_price_minor ASC, t.id;

\echo '=== Q4: women, size 8 in stock, newest ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
        AND (p.gender = 'women' OR p.gender = 'unisex')
        AND EXISTS (SELECT 1 FROM variants sv
                    WHERE sv.product_id = p.id AND sv.active AND sv.stock > 0 AND sv.size_label = '8')
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT 24 OFFSET 0) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.created_at DESC, t.id DESC;

\echo '=== Q5: deep page (offset 960), newest ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT 24 OFFSET 960) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.created_at DESC, t.id DESC;

\echo '=== Q6: the count query that runs beside every listing ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*) FROM products p WHERE p.active AND p.min_price_minor IS NOT NULL;

\echo '=== Q7: price range 200-300 dollars, price ascending (new) ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT t.id, t.slug, t.name, t.brand, t.gender, cat.slug AS category_slug, cat.name AS category_name,
       t.min_price_minor AS min_price, v.currency, v.in_stock
FROM (SELECT p.id, p.slug, p.name, p.brand, p.gender, p.category_id, p.min_price_minor, p.created_at
      FROM products p
      WHERE p.active AND p.min_price_minor IS NOT NULL
        AND p.min_price_minor >= 20000 AND p.min_price_minor <= 30000
      ORDER BY p.min_price_minor ASC, p.id
      LIMIT 24 OFFSET 0) t
JOIN categories cat ON cat.id = t.category_id
CROSS JOIN LATERAL (SELECT min(currency) AS currency, bool_or(stock > 0) AS in_stock
                    FROM variants WHERE product_id = t.id AND active) v
ORDER BY t.min_price_minor ASC, t.id;
