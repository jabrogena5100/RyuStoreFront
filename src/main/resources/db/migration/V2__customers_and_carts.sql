-- Customers and carts. A cart belongs to exactly one owner: a signed-in customer, or a guest
-- identified by the hash of a signed cookie token (the raw token is never stored).

CREATE TABLE customers (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    display_name  text,
    role          text        NOT NULL DEFAULT 'customer',
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customers_email_uniq UNIQUE (email),
    CONSTRAINT customers_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT customers_email_nonblank CHECK (length(btrim(email)) > 0),
    CONSTRAINT customers_role_valid CHECK (role IN ('customer', 'admin'))
);

CREATE TABLE carts (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      bigint,
    guest_token_hash text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT carts_customer_fk FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE CASCADE,
    CONSTRAINT carts_customer_uniq UNIQUE (customer_id),
    CONSTRAINT carts_guest_token_uniq UNIQUE (guest_token_hash),
    CONSTRAINT carts_one_owner CHECK ((customer_id IS NOT NULL) <> (guest_token_hash IS NOT NULL))
);

CREATE TABLE cart_items (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id    uuid        NOT NULL,
    variant_id bigint      NOT NULL,
    quantity   integer     NOT NULL,
    added_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT cart_items_cart_fk FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT cart_items_variant_fk FOREIGN KEY (variant_id) REFERENCES variants (id) ON DELETE RESTRICT,
    CONSTRAINT cart_items_cart_variant_uniq UNIQUE (cart_id, variant_id),
    CONSTRAINT cart_items_quantity_range CHECK (quantity BETWEEN 1 AND 10)
);
CREATE INDEX cart_items_variant_id_idx ON cart_items (variant_id);
