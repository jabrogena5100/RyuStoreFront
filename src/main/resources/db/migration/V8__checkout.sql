-- Checkout start (slice 3d).
-- idempotency_key: a client-generated key makes "start checkout" safe to retry; unique per customer.
-- expires_at: when a pending order stops holding stock (the reservation expiry); the worker (phase 4)
-- cancels pending orders past it. Both are nullable so orders created any other way stay valid.

ALTER TABLE orders
    ADD COLUMN idempotency_key text,
    ADD COLUMN expires_at      timestamptz;

ALTER TABLE orders
    ADD CONSTRAINT orders_idempotency_key_length CHECK (idempotency_key IS NULL OR length(idempotency_key) BETWEEN 8 AND 100);

CREATE UNIQUE INDEX orders_customer_idempotency_uniq ON orders (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX orders_pending_expiry_idx ON orders (expires_at) WHERE status = 'pending';
