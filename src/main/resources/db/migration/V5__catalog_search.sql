-- Full-text search and the indexes the catalog queries are expected to use.
-- These are hypotheses until EXPLAIN output (docs/explain) confirms them; adjust from evidence.

-- Weighted document: name (A), brand (B), description (C). 'english' stems "boots" -> "boot".
ALTER TABLE products ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(brand, '')), 'B') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'C')
) STORED;

CREATE INDEX products_search_idx ON products USING gin (search_vector);

-- Default listing: newest first, active products only.
CREATE INDEX products_active_newest_idx ON products (created_at DESC, id DESC) WHERE active;

-- Brand filter.
CREATE INDEX products_brand_idx ON products (brand) WHERE active;

-- Per-product price / stock aggregate (the LATERAL subquery) as an index-only scan.
CREATE INDEX variants_product_cover_idx ON variants (product_id) INCLUDE (price_minor, currency, stock) WHERE active;

-- Size filter: "does this product have this size in stock".
CREATE INDEX variants_size_idx ON variants (size_label, product_id) WHERE active AND stock > 0;
