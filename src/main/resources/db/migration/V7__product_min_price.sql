-- Denormalised cheapest active price per product, so price sorts, price filters and counts can use
-- an index instead of aggregating every variant of every matching product (measured at 50,000
-- products: 651 ms for "boots by price" and 581 ms for the count; see docs/explain).
--
-- NULL means the product has no active variant and is not purchasable (hidden from listings).
-- Kept in sync by triggers on variants. Stock updates do not fire any of them (the reservation
-- hot path), only inserts, deletes and changes to price_minor or active.

ALTER TABLE products ADD COLUMN min_price_minor bigint;
ALTER TABLE products ADD CONSTRAINT products_min_price_nonneg CHECK (min_price_minor IS NULL OR min_price_minor >= 0);

UPDATE products p
SET min_price_minor = (SELECT min(v.price_minor) FROM variants v WHERE v.product_id = p.id AND v.active);

CREATE FUNCTION variants_sync_min_price() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    pid bigint;
BEGIN
    IF TG_OP = 'INSERT' THEN
        -- Incremental and O(1): only touch the product row when the new variant lowers the minimum.
        IF NEW.active THEN
            UPDATE products
            SET min_price_minor = NEW.price_minor
            WHERE id = NEW.product_id
              AND (min_price_minor IS NULL OR NEW.price_minor < min_price_minor);
        END IF;
        RETURN NULL;
    END IF;

    -- UPDATE (price or active changed) or DELETE: recompute for the product. Rare operations.
    pid := CASE WHEN TG_OP = 'DELETE' THEN OLD.product_id ELSE NEW.product_id END;
    UPDATE products
    SET min_price_minor = (SELECT min(v.price_minor) FROM variants v WHERE v.product_id = pid AND v.active)
    WHERE id = pid;
    RETURN NULL;
END;
$$;

CREATE TRIGGER variants_min_price_ins AFTER INSERT ON variants
    FOR EACH ROW EXECUTE FUNCTION variants_sync_min_price();

CREATE TRIGGER variants_min_price_upd AFTER UPDATE ON variants
    FOR EACH ROW
    WHEN (OLD.price_minor IS DISTINCT FROM NEW.price_minor OR OLD.active IS DISTINCT FROM NEW.active)
    EXECUTE FUNCTION variants_sync_min_price();

CREATE TRIGGER variants_min_price_del AFTER DELETE ON variants
    FOR EACH ROW EXECUTE FUNCTION variants_sync_min_price();

-- Price sort and range filter; price sort within a category (and the category row count).
CREATE INDEX products_price_idx ON products (min_price_minor, id)
    WHERE active AND min_price_minor IS NOT NULL;
CREATE INDEX products_category_price_idx ON products (category_id, min_price_minor, id)
    WHERE active AND min_price_minor IS NOT NULL;
