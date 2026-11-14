-- Query plans for the main catalog queries. Run against a seeded database:
--   scripts/explain-catalog.sh
-- The statements mirror ProductSearchRepository (literal values instead of bind parameters).
\pset pager off
\echo '=== table sizes ==='
SELECT (SELECT count(*) FROM products) AS products, (SELECT count(*) FROM variants) AS variants;

-- VACUUM sets the visibility map so index-only scans do not fall back to the heap; ANALYZE refreshes statistics.
VACUUM (ANALYZE);

\echo '=== Q1: default listing, newest first, page 1 ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.slug, p.name, p.brand, p.gender, cat.slug, cat.name, v.min_price, v.currency, v.in_stock
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 24 OFFSET 0;

\echo '=== Q2: full-text search "chelsea boot", ranked ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.slug, p.name, p.brand, p.gender, cat.slug, cat.name, v.min_price, v.currency, v.in_stock
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL
  AND p.search_vector @@ websearch_to_tsquery('english', 'chelsea boot')
ORDER BY ts_rank_cd(p.search_vector, websearch_to_tsquery('english', 'chelsea boot')) DESC, p.id
LIMIT 24 OFFSET 0;

\echo '=== Q3: category boots, price ascending ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.slug, p.name, p.brand, p.gender, cat.slug, cat.name, v.min_price, v.currency, v.in_stock
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL
  AND (cat.slug = 'boots' OR cat.parent_id = (SELECT id FROM categories WHERE slug = 'boots'))
ORDER BY v.min_price ASC, p.id
LIMIT 24 OFFSET 0;

\echo '=== Q4: women, size 8 in stock, newest ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.slug, p.name, p.brand, p.gender, cat.slug, cat.name, v.min_price, v.currency, v.in_stock
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL
  AND (p.gender = 'women' OR p.gender = 'unisex')
  AND EXISTS (SELECT 1 FROM variants sv WHERE sv.product_id = p.id AND sv.active AND sv.stock > 0 AND sv.size_label = '8')
ORDER BY p.created_at DESC, p.id DESC
LIMIT 24 OFFSET 0;

\echo '=== Q5: deep page (offset 960), newest ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.id, p.slug, p.name, p.brand, p.gender, cat.slug, cat.name, v.min_price, v.currency, v.in_stock
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL
ORDER BY p.created_at DESC, p.id DESC
LIMIT 24 OFFSET 960;

\echo '=== Q6: the count query that runs beside every listing ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT count(*)
FROM products p
JOIN categories cat ON cat.id = p.category_id
CROSS JOIN LATERAL (
    SELECT min(price_minor) AS min_price, min(currency) AS currency, bool_or(stock > 0) AS in_stock
    FROM variants WHERE product_id = p.id AND active
) v
WHERE p.active AND v.min_price IS NOT NULL;
