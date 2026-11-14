-- Ledger of emails already sent, so an event delivered twice (the outbox is at-least-once) does not
-- email the customer twice. dedupe_key identifies the email, for example 'order.paid:<order id>'.
-- The row is written in the same transaction as the send: if sending fails, the row is rolled back and
-- the event is retried.

CREATE TABLE sent_emails (
    dedupe_key text        PRIMARY KEY,
    recipient  text        NOT NULL,
    subject    text        NOT NULL,
    sent_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT sent_emails_key_nonblank CHECK (length(btrim(dedupe_key)) > 0),
    CONSTRAINT sent_emails_recipient_nonblank CHECK (length(btrim(recipient)) > 0)
);
