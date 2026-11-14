# Payment API contract (slice 4.4a: starting payment)

The contract for the "Pay now" step: after checkout has created a pending order, the shopper is sent to
Stripe's hosted payment page. **Implemented, unverified until `docs/verification.md` says otherwise.** Marking
the order paid (Stripe's webhook), the confirmation page and the cart being emptied come in slice 4.4b.

Same-origin, same error body as the other APIs (`{code, message, fields}`), CSRF header on the POST.
**No card details ever touch this application or the browser app**: the shopper pays on Stripe's page.

## Endpoint

`POST /api/orders/{orderId}/pay` (signed in, CSRF header, no body)

| Result | Status | Body |
| --- | --- | --- |
| Payment page created, or already created and still valid | 200 | `{ "url": "https://checkout.stripe.com/...", "expiresAt": "2026-11-14T10:41:00Z" }` |
| Not signed in | 401 `unauthenticated` | |
| Order unknown, or not yours (deliberately the same answer) | 404 `order_not_found` | |
| Order is not pending (paid, cancelled, ...) | 409 `order_not_payable` | |
| The order's time to pay ran out | 409 `order_expired` | |
| The order's payment page already expired | 409 `payment_expired` | |
| Payments not configured on this server | 503 `payments_unavailable` | |
| Stripe failed or rejected the request | 502 `payment_provider_error` (safe to retry) | |
| Missing CSRF | 403 `csrf_invalid` | |

The frontend redirects the browser to `url`. Calling again returns the **same** page while it is valid, so
double clicks and a second tab are safe.

## Times (what the design should show)

- The payment page lasts about **31 minutes** from the moment it is created (Stripe requires at least 30).
- The shopper's stock is held for that time **plus 5 minutes**. Creating the page extends the hold that
  checkout started, so a shopper who is slow to press "Pay" still has stock for the whole page.
- An order gets **one** payment page. When it has expired the shopper starts a new checkout
  (`payment_expired` / `order_expired`). This bounds how long stock can be held.

After Stripe takes payment the shopper is returned to
`{public base URL}/orders/{orderId}/confirmation?session_id=...`, or to `{public base URL}/cart` if they
cancel. Do not treat arriving on the confirmation URL as proof of payment: the order is marked paid only by
Stripe's webhook (4.4b), so that page must show "confirming your payment" until the order says `paid`.

## States to design

| Screen | States |
| --- | --- |
| Pay button | idle, starting (busy), redirecting to Stripe, `payments_unavailable` (try later), `payment_provider_error` (retry), `order_expired` / `payment_expired` (explain, offer a fresh checkout) |
| Back from Stripe: success URL | "confirming your payment" until the order reads `paid` (poll or refresh); then the confirmation |
| Back from Stripe: cancel URL | the cart, with a note that nothing was charged and the bag is still held for a few minutes |
