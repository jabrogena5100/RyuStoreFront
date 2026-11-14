-- Stock reservations and the transactional outbox.
--
-- A reservation holds units for an order until it is committed (paid) or released (expired or
-- cancelled). Taking units out of variants.stock happens with an atomic
--   UPDATE variants SET stock = stock - :n WHERE id = :id AND stock >= :n
-- in application code; the CHECK (stock >= 0) constraint is the backstop.

CREATE TABLE reservations (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    uuid        NOT NULL,
    variant_id  bigint      NOT NULL,
    quantity    integer     NOT NULL,
    status      text        NOT NULL DEFAULT 'active',
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    CONSTRAINT reservations_order_fk FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT reservations_variant_fk FOREIGN KEY (variant_id) REFERENCES variants (id) ON DELETE RESTRICT,
    CONSTRAINT reservations_order_variant_uniq UNIQUE (order_id, variant_id),
    CONSTRAINT reservations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT reservations_status_valid CHECK (status IN ('active', 'committed', 'released')),
    CONSTRAINT reservations_resolved_matches_status CHECK ((status = 'active') = (resolved_at IS NULL))
);
-- The worker's "what has expired?" query only ever looks at active rows.
CREATE INDEX reservations_active_expiry_idx ON reservations (expires_at) WHERE status = 'active';
CREATE INDEX reservations_variant_id_idx ON reservations (variant_id);

-- Events are inserted in the same transaction as the state change that caused them, then
-- published by the worker. processed_at IS NULL means still pending.
CREATE TABLE outbox (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type text        NOT NULL,
    aggregate_id   text        NOT NULL,
    event_type     text        NOT NULL,
    payload        jsonb       NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    available_at   timestamptz NOT NULL DEFAULT now(),
    attempts       integer     NOT NULL DEFAULT 0,
    last_error     text,
    processed_at   timestamptz,
    CONSTRAINT outbox_attempts_nonneg CHECK (attempts >= 0)
);
CREATE INDEX outbox_pending_idx ON outbox (available_at, id) WHERE processed_at IS NULL;
