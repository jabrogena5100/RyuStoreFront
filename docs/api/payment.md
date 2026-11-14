# Payment API contract (slice 4.4a: starting payment)

The contract for the "Pay now" step: after checkout has created a pending order, the shopper is sent to
Stripe's hosted payment page. **Implemented, unverified until `docs/verification.md` says otherwise.** Slice 4.4b
(below) adds Stripe's webhook, which is what marks the order paid and empties the cart.

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
Stripe's webhook (4.4b), so that page must show "confirming your payment" until the order says `paid` (see "After payment" below).

## States to design

| Screen | States |
| --- | --- |
| Pay button | idle, starting (busy), redirecting to Stripe, `payments_unavailable` (try later), `payment_provider_error` (retry), `order_expired` / `payment_expired` (explain, offer a fresh checkout) |
| Back from Stripe: success URL | "confirming your payment" until the order reads `paid` (poll or refresh); then the confirmation |
| Back from Stripe: cancel URL | the cart, with a note that nothing was charged and the bag is still held for a few minutes |

## After payment (slice 4.4b): what the frontend can rely on

The shopper's browser never tells the server it paid. Stripe calls `POST /api/stripe/webhook` (server to server,
not part of the frontend's contract; documented in `docs/payments.md`). What changes for the UI:

- The order's `status` becomes `paid` **a moment after** the shopper lands on the success URL, not before. The
  confirmation page must poll or refresh the order until `status` is `paid` (or `cancelled`/`refunded`) and show
  "confirming your payment" meanwhile. Webhook delivery normally takes a second or two; Stripe retries for days
  if this server is down, so "still confirming" can in rare cases last long. Offer a way back to order history.
- The **cart is emptied only after the order is paid** (a few seconds later, when the background worker runs).
  A shopper who cancels on Stripe's page, or whose page expires, keeps their cart. Lines added after checkout
  started are not removed.
- The confirmation e-mail arrives after the same worker run.
- Two end states the UI must be able to show besides `paid`:
  - `cancelled`: the payment page expired, or the shopper paid too late and the money is being refunded;
  - `refunded`: payment succeeded but the stock could not be kept, or the amount did not match, and the
    payment was refunded. Say that nothing will be shipped and the money is on its way back (a few days).
