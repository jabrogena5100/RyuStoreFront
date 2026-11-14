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
| `STRIPE_WEBHOOK_SECRET` | `whsec_...`, used by the webhook in 4.4b (from `stripe listen`, or the dashboard endpoint). |
| `STOREFRONT_PUBLIC_BASE_URL` | where the shopper returns to (default `http://localhost:3000`, the future frontend). |

`storefront.payment.session-minutes` (31) and `storefront.payment.hold-grace-minutes` (5) tune the times.

## Known limits (recorded, not hidden)

- The Stripe call happens while the order row is locked (one row, one call, timeouts of 10 s and 20 s). That is
  what makes simultaneous clicks create one session; it also means a slow Stripe holds that one order's lock.
- A crash after Stripe created the session but before our commit leaves an orphan session at Stripe. It is
  harmless (nobody has its URL and it expires on its own).
- Not verified against real Stripe until you run the manual check with your test key: automated tests use the
  fake gateway, and the request mapping is checked offline (`StripeCheckoutParamsTests`).
- One payment page per order; an expired page means a new checkout.
