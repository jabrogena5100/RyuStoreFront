# Checkout API contract (slice 3d: starting a checkout)

The contract for the checkout form (contact, shipping address) and the "place order" step that sends the
shopper to payment. Paste it into Claude Design as the list of states to cover. **Implemented; treat as
unverified until `docs/verification.md` says otherwise.** Payment itself (Stripe), order confirmation and
emails are phase 4.

Same-origin, same error body as the other APIs (`{code, message, fields}`), CSRF header on the POST.

## Who can check out

Guests can fill a cart, but **buying requires an account**. A guest who presses checkout gets
`401 unauthenticated`: the design should send them to Sign in / Create account and bring them back. Their
guest cart is merged into their account cart at sign-in (see `docs/api/cart.md`), so nothing is lost.

## Endpoint

`POST /api/checkout`, headers `X-CSRF-TOKEN` and **`Idempotency-Key`**.

The key is a UUID the client generates **once per "Place order" attempt** and reuses on every retry of that
attempt (network error, double click). Same key: the server returns the same order (200) instead of creating
another. Generate a new key when the shopper changes anything and submits again.

```json
{
  "contactEmail": "optional; defaults to the account email",
  "shippingAddress": {
    "name": "Ada Buyer", "line1": "1 Main St", "line2": "optional",
    "city": "Portland", "region": "OR", "postalCode": "97201", "country": "US"
  }
}
```

| Result | Status | Body |
| --- | --- | --- |
| Order created, stock held | 201 | the order (below) |
| Same `Idempotency-Key` again | 200 | the same order, in whatever state it is now (it may be `cancelled`) |
| Not signed in | 401 `unauthenticated` | |
| Missing or malformed key | 400 `validation_failed`, `fields.idempotencyKey`: `required`, `invalid_format` | |
| Invalid form | 400 `validation_failed` with field codes | |
| Empty cart | 409 `empty_cart` | |
| Cart no longer purchasable | 409 `cart_changed`, `fields` is `{ "<sku>": "unavailable" \| "stock" }` for **every** problem line | |
| Missing CSRF | 403 `csrf_invalid` | |

Field codes: `shippingAddress.<field>` and `contactEmail` use `required`, `length`, `invalid_country` (country
must be two capital letters), `invalid_email` (at most 100 printable ASCII characters).

## The order

```json
{
  "id": "uuid", "status": "pending", "contactEmail": "...", "currency": "USD",
  "subtotalMinor": 30980, "shippingMinor": 800, "totalMinor": 31780,
  "expiresAt": "2026-11-14T10:00:00Z", "createdAt": "...",
  "items": [ { "sku": "...", "productName": "...", "sizeLabel": "42", "colour": "black",
               "unitPriceMinor": 12990, "quantity": 2, "lineTotalMinor": 25980 } ]
}
```

- Money is integer minor units. Shipping is a flat rate (config, default 800 = USD 8.00).
- `items` are a **snapshot**: the price and names at the moment of checkout. Later catalogue changes do not
  alter the order.
- **Stock is held until `expiresAt`** (default 30 minutes). Show a countdown on the payment step. After it,
  the stock is released and the order will be cancelled by the worker (phase 4).

## What happens to earlier attempts

Starting a checkout with a **new** key cancels the shopper's previous pending order and gives its stock
back first. So changing the address and pressing "Place order" again never competes with the abandoned
attempt for the last pair. If the new attempt fails (for example `cart_changed`), nothing changes: the
earlier pending order stays pending.

The cart itself is **not** emptied at this step; it is cleared when payment succeeds (phase 4). Someone who
backs out of the payment page still has their bag.

## States to design

| Screen | States |
| --- | --- |
| Checkout form | idle, validation errors per field (codes above), submitting (disabled, busy), generic error with retry (same key) |
| Signed-out visitor at checkout | sign in / create account prompt that returns to checkout with the bag intact |
| `cart_changed` | per-line markers using the sku keys: "no longer available" (`unavailable`), "only fewer left" (`stock`), with remove / reduce actions; the total must refresh |
| `empty_cart` | empty state linking back to the catalogue |
| Order held | summary from the response, hold countdown from `expiresAt`, what happens if it runs out |
| Expired | the hold ended; offer "start again" (new key) |

## Known limits (recorded, not hidden)

- Reusing a key with a **different** body returns the original order and ignores the new body; it is not
  rejected. Generate a new key for a changed request.
- One currency (USD), flat shipping, no tax: all out of v1 scope.
- Nothing yet moves a pending order to `paid`, and nothing cancels an expired one (phase 4).
- The hold TTL equals Stripe's minimum Checkout session lifetime only as far as I recall it; confirm against
  Stripe's documentation before phase 4 relies on it.
