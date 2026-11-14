# Payments (Stripe, test mode)

Payment uses **Stripe Checkout, the hosted page**: the shopper types their card on Stripe's domain, so no card
data reaches this application or the browser app. We are in **test mode only**; live keys are out of scope.

## Slice 4.4a: starting payment

```
checkout start  ─►  pending order + stock held 30 min
POST /api/orders/{id}/pay  ─►  lock the order ─► create a Stripe Checkout Session (31 min)
                               store session id + url + expiry on the order
                               extend the hold to  session expiry + 5 min
```

`PaymentGateway` is an interface: `StripePaymentGateway` is the real one; tests install a fake. With no
`STRIPE_SECRET_KEY` the shop runs and `pay` answers 503.

### Why the hold is extended

Stripe's documentation: a Checkout Session's `expires_at` can be "anywhere from 30 minutes to 24 hours after
Checkout Session creation" (checked 2026-11-14, <https://docs.stripe.com/payments/checkout/how-checkout-works>).
Checkout's own hold is 30 minutes from *checkout start*, which is earlier than any session created later, so
without extending it a shopper could pay for stock that had already been given back. Creating the page moves
the order's hold and reservations out to the page's expiry plus a 5 minute grace.

### Configuration

| Variable | Meaning |
| --- | --- |
| `STRIPE_SECRET_KEY` | `sk_test_...` from the Stripe dashboard (test mode). Never commit it; put it in `.env`. |
| `STRIPE_WEBHOOK_SECRET` | `whsec_...`, the signing secret of the webhook endpoint (printed by `stripe listen`, or from the dashboard endpoint). With none set the webhook answers 503 and never accepts unsigned input. |
| `STOREFRONT_PUBLIC_BASE_URL` | where the shopper returns to (default `http://localhost:3000`, the future frontend). |

`storefront.payment.session-minutes` (31), `storefront.payment.hold-grace-minutes` (5) and `storefront.payment.webhook-tolerance-seconds` (300) tune the times.

## Slice 4.4b: the webhook (how an order becomes paid)

`POST /api/stripe/webhook` is the **only** thing that marks an order paid. It is public (Stripe has no session or
CSRF token); the credential is Stripe's signature.

```
Stripe ──POST raw JSON + Stripe-Signature──►  verify HMAC over the RAW bytes (SDK Webhook.Signature, 5 min tolerance)
                                                │ bad / missing / stale ──► 400
                                                ▼
                                    one transaction:
                                      INSERT stripe_events (event id) ON CONFLICT DO NOTHING
                                        │ nothing inserted (a repeat) ──► 200, done
                                        ▼
                                      by event type (below)  ──► mark processed ──► 200
                                      any exception ──► everything rolls back (even the event id) ──► 500, Stripe retries
```

The payload is read with Gson (`id`, `type`, and for `checkout.session.*` events the session's `id`,
`payment_status`, `payment_intent`, `amount_total`, `currency`, `metadata.order_id`), **not** with the SDK's typed
event classes, which are generated for one API version and fail on another. Tests use an event with
`api_version` `2099-01-01.future` to keep that true.

| Event | What happens |
| --- | --- |
| `checkout.session.completed` with `payment_status = paid`, and `checkout.session.async_payment_succeeded` | Pay the order (below) |
| `checkout.session.completed` with any other status | Nothing yet (a delayed payment method; the async event follows) |
| `checkout.session.expired`, `checkout.session.async_payment_failed` | Cancel the order and release its stock, **only** if the event's session id equals the order's `stripe_checkout_session_id`; a paid order stays paid |
| any other type, an unknown or malformed order id | 200, recorded, nothing else |

### Paying an order

All in the event's transaction, order lock first:

1. The session's `amount_total` and `currency` must equal the order's totals. If not: cancel the order (its stock
   returns), record what Stripe took and queue a refund. (If the order is already paid or beyond, a mismatched
   second payment is logged for a human, not refunded automatically.)
2. `Orders.transition(PAID)`:
   - **applied:** insert the `payments` row, then `StockReservations.commit`. If commit changes 0 rows the hold is
     gone: queue a refund and move `paid` to `refunded` (the `order.paid` event still exists, so the e-mail and
     cart handlers check the order's status and do nothing for `refunded`/`cancelled`).
   - **already paid:** nothing (a repeat or the twin event).
   - **illegal, order was `cancelled`:** a late payment. Insert the `payments` row (once) and queue a refund. The
     order stays `cancelled`.
   - **illegal, any other status:** an earlier event already handled it; nothing.
3. A `payments` insert uses `ON CONFLICT DO NOTHING` on both unique constraints (one per order, one per
   PaymentIntent), and a refund is queued only when that insert was new, so a second paid event cannot queue a
   second refund.

### Refunds and the cart

- A refund is an outbox event `payment.refund_requested`; `PaymentRefundHandler` calls
  `PaymentGateway.refund(paymentIntentId, "refund-order-<orderId>")` (Stripe idempotency key, so a redelivery
  is the same refund; a payment already fully refunded counts as done) and sets `payments.status = 'refunded'`.
  Failures retry with the worker's backoff.
- The cart is emptied when the order is paid, by a second `order.paid` handler (`OrderPaidCartHandler`, in
  `checkout`): it deletes the cart lines for the variants in the order. Lines added after checkout stay; a shopper
  who never pays keeps their cart. This is why the outbox now supports several handlers per event type.

### Local manual check (Stripe test mode)

1. Install the Stripe CLI from Stripe's own instructions (<https://docs.stripe.com/stripe-cli/install>), then
   `stripe login` (browser pairing) once.
2. Put your test secret key in `.env` as `STRIPE_SECRET_KEY=sk_test_...`.
3. Get the webhook signing secret: `stripe listen --print-secret` prints it and exits (it does not change between
   runs); put it in `.env` as `STRIPE_WEBHOOK_SECRET=whsec_...`.
4. `set -a; source .env; set +a`, `docker compose up -d`, then the API and the worker (see `docs/worker.md`).
5. In a third terminal keep this running, and watch it print each event and the `200` our endpoint answers:
   `stripe listen --events checkout.session.completed,checkout.session.expired,checkout.session.async_payment_succeeded,checkout.session.async_payment_failed --forward-to localhost:8080/api/stripe/webhook`
6. `bash scripts/manual-checkout.sh <sku>` creates a throwaway customer, a cart, an order and the payment page;
   open the printed URL and pay with `4242 4242 4242 4242`.
7. Check: the order is `paid` in Postgres, one row in `payments`, one e-mail in Mailpit
   (<http://localhost:8025>), and the cart no longer holds the purchased line.

## Known limits (recorded, not hidden)

- The webhook's secret comes from the environment; rotating it needs a restart. No Stripe API-version pinning
  is needed because the payload is read leniently, but a changed field name in a future version would show up as
  "unknown order" (200, nothing happens); the manual check and `stripe_events` rows are how to notice.
- A paid session with no `payment_intent` (cannot happen in payment mode) answers 500 on purpose and is retried
  by Stripe; it will not heal by itself.
- A mismatched payment for an order that is already paid is logged, not refunded: a person has to look.
- The refund for a cancelled order's late payment leaves the order `cancelled`, not `refunded`.
- Not verified against real Stripe until you run the manual check with your test key.
- The Stripe call happens while the order row is locked (one row, one call, timeouts of 10 s and 20 s). That is
  what makes simultaneous clicks create one session; it also means a slow Stripe holds that one order's lock.
- A crash after Stripe created the session but before our commit leaves an orphan session at Stripe. It is
  harmless (nobody has its URL and it expires on its own).
- The pay step is not verified against real Stripe either (the fake gateway and `StripeCheckoutParamsTests` only).
- One payment page per order; an expired page means a new checkout.
