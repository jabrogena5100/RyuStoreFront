-- The Stripe Checkout Session attached to an order (slice 4.4). The three columns are set together:
-- stripe_checkout_session_id (V3, unique), the hosted payment page URL, and when that page stops working.

ALTER TABLE orders
    ADD COLUMN payment_url        text,
    ADD COLUMN payment_expires_at timestamptz;

ALTER TABLE orders
    ADD CONSTRAINT orders_payment_session_complete CHECK (
        (stripe_checkout_session_id IS NULL) = (payment_url IS NULL)
        AND (payment_url IS NULL) = (payment_expires_at IS NULL));
