-- Orders, order items (snapshots of what was bought and at what price), status history,
-- payments, and the Stripe event log used for webhook idempotency.
-- Legal status transitions are enforced in application code (state machine); the database
-- only guarantees that a status is one of the known values.

CREATE TABLE orders (
    id                         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id                bigint,
    contact_email              text        NOT NULL,
    status                     text        NOT NULL DEFAULT 'pending',
    currency                   char(3)     NOT NULL DEFAULT 'USD',
    subtotal_minor             bigint      NOT NULL,
    shipping_minor             bigint      NOT NULL,
    total_minor                bigint      NOT NULL,
    shipping_address           jsonb       NOT NULL,
    stripe_checkout_session_id text,
    version                    bigint      NOT NULL DEFAULT 0,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT orders_customer_fk FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE RESTRICT,
    CONSTRAINT orders_stripe_session_uniq UNIQUE (stripe_checkout_session_id),
    CONSTRAINT orders_status_valid CHECK (status IN ('pending', 'paid', 'fulfilled', 'shipped', 'cancelled', 'refunded')),
    CONSTRAINT orders_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT orders_amounts_nonneg CHECK (subtotal_minor >= 0 AND shipping_minor >= 0 AND total_minor >= 0),
    CONSTRAINT orders_total_matches CHECK (total_minor = subtotal_minor + shipping_minor)
);
CREATE INDEX orders_customer_id_idx ON orders (customer_id, created_at DESC);
CREATE INDEX orders_status_created_idx ON orders (status, created_at);

CREATE TABLE order_items (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         uuid    NOT NULL,
    variant_id       bigint  NOT NULL,
    sku              text    NOT NULL,
    product_name     text    NOT NULL,
    size_label       text    NOT NULL,
    colour           text    NOT NULL,
    unit_price_minor bigint  NOT NULL,
    quantity         integer NOT NULL,
    CONSTRAINT order_items_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_items_variant_fk FOREIGN KEY (variant_id) REFERENCES variants (id) ON DELETE RESTRICT,
    CONSTRAINT order_items_order_variant_uniq UNIQUE (order_id, variant_id),
    CONSTRAINT order_items_price_nonneg CHECK (unit_price_minor >= 0),
    CONSTRAINT order_items_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX order_items_variant_id_idx ON order_items (variant_id);

CREATE TABLE order_status_history (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    uuid        NOT NULL,
    from_status text,
    to_status   text        NOT NULL,
    reason      text,
    changed_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT order_status_history_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT order_status_history_to_valid CHECK (to_status IN ('pending', 'paid', 'fulfilled', 'shipped', 'cancelled', 'refunded')),
    CONSTRAINT order_status_history_from_valid CHECK (from_status IS NULL OR from_status IN ('pending', 'paid', 'fulfilled', 'shipped', 'cancelled', 'refunded'))
);
CREATE INDEX order_status_history_order_idx ON order_status_history (order_id, changed_at);

-- One payment record per order in v1 (one Checkout Session per order). The unique constraints
-- are what make a replayed webhook harmless: a second insert for the same order or the same
-- PaymentIntent is rejected by the database, not just by application logic.
CREATE TABLE payments (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id                 uuid        NOT NULL,
    stripe_payment_intent_id text        NOT NULL,
    amount_minor             bigint      NOT NULL,
    refunded_minor           bigint      NOT NULL DEFAULT 0,
    currency                 char(3)     NOT NULL DEFAULT 'USD',
    status                   text        NOT NULL DEFAULT 'succeeded',
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payments_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT payments_order_uniq UNIQUE (order_id),
    CONSTRAINT payments_intent_uniq UNIQUE (stripe_payment_intent_id),
    CONSTRAINT payments_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT payments_refund_within_amount CHECK (refunded_minor >= 0 AND refunded_minor <= amount_minor),
    CONSTRAINT payments_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT payments_status_valid CHECK (status IN ('succeeded', 'partially_refunded', 'refunded'))
);

-- Every Stripe event id we have seen. INSERT ... ON CONFLICT DO NOTHING tells the handler
-- whether this delivery is new or a retry.
CREATE TABLE stripe_events (
    event_id     text        NOT NULL,
    event_type   text        NOT NULL,
    received_at  timestamptz NOT NULL DEFAULT now(),
    processed_at timestamptz,
    CONSTRAINT stripe_events_pk PRIMARY KEY (event_id)
);
