-- Catalog: categories, products, variants (size x colour, each with its own price and stock).
-- Money is integer minor units (cents) plus an ISO currency code, never floating point.
-- variants.stock is the number of units still available to sell; reservations (V4) take units out of it.

CREATE TABLE categories (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug      text   NOT NULL,
    name      text   NOT NULL,
    parent_id bigint REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT categories_slug_uniq UNIQUE (slug),
    CONSTRAINT categories_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT categories_name_nonblank CHECK (length(btrim(name)) > 0),
    CONSTRAINT categories_not_own_parent CHECK (parent_id IS DISTINCT FROM id)
);
CREATE INDEX categories_parent_id_idx ON categories (parent_id);

CREATE TABLE products (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug        text        NOT NULL,
    name        text        NOT NULL,
    brand       text        NOT NULL,
    description text        NOT NULL DEFAULT '',
    category_id bigint      NOT NULL,
    gender      text        NOT NULL,
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT products_slug_uniq UNIQUE (slug),
    CONSTRAINT products_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT products_name_nonblank CHECK (length(btrim(name)) > 0),
    CONSTRAINT products_brand_nonblank CHECK (length(btrim(brand)) > 0),
    CONSTRAINT products_gender_valid CHECK (gender IN ('women', 'men', 'unisex')),
    CONSTRAINT products_category_fk FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);
CREATE INDEX products_category_id_idx ON products (category_id);

CREATE TABLE variants (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id  bigint   NOT NULL,
    sku         text     NOT NULL,
    size_label  text     NOT NULL,
    colour      text     NOT NULL,
    price_minor bigint   NOT NULL,
    currency    char(3)  NOT NULL DEFAULT 'USD',
    stock       integer  NOT NULL DEFAULT 0,
    active      boolean  NOT NULL DEFAULT true,
    CONSTRAINT variants_product_fk FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT variants_sku_uniq UNIQUE (sku),
    CONSTRAINT variants_product_size_colour_uniq UNIQUE (product_id, size_label, colour),
    CONSTRAINT variants_sku_nonblank CHECK (length(btrim(sku)) > 0),
    CONSTRAINT variants_size_nonblank CHECK (length(btrim(size_label)) > 0),
    CONSTRAINT variants_colour_nonblank CHECK (length(btrim(colour)) > 0),
    CONSTRAINT variants_price_nonneg CHECK (price_minor >= 0),
    CONSTRAINT variants_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT variants_stock_nonneg CHECK (stock >= 0)
);
