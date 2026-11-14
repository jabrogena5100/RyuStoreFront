# Cart API contract (slice 3c)

The contract the designed cart screens, the header cart badge and the product page "Add to bag" are built
against. Paste it into Claude Design as the list of states to cover. **Implemented and, once
`docs/verification.md` says so, verified; until then treat it as a plan.**

Same-origin, same error body as the auth API (`{code, message, fields}`, see `docs/api/auth.md`).
Every write (PUT, DELETE) needs the CSRF header; call `GET /api/auth/csrf` on page load as before.

## Who owns the cart

- **Signed in:** the customer's single cart. No cart cookie.
- **Guest:** a cart identified by the `CART` cookie (HttpOnly, SameSite=Lax, Secure when deployed,
  30 days, renewed on every write). The cookie is 256 random bits; the server keeps only its SHA-256.
  JavaScript never sees it.
- **Reading never creates anything.** `GET /api/cart` for a visitor with no cart returns an empty cart and
  sets no cookie. The first successful write creates the cart (and the cookie, for guests).
- **On sign-in or registration** the guest cart is merged into the customer's cart and the `CART` cookie is
  retired. For a SKU in both carts the **larger** quantity wins (it is not added up). After login, call
  `GET /api/cart` again to show the merged result.
- A forged, expired or garbled cookie is not an error: it behaves like no cookie.

## Endpoints

| Method and path | Body | Success | Errors |
| --- | --- | --- | --- |
| `GET /api/cart` | none | 200 cart | none |
| `PUT /api/cart/items/{sku}` | `{"quantity": 0..10}` | 200 cart (the whole updated cart) | 400 `validation_failed` (`fields.quantity`: `required`, `min`, `max`), 400 `malformed_request`, 404 `unknown_item`, 409 `insufficient_stock` (`fields.quantity`: `stock`), 403 `csrf_invalid` |
| `DELETE /api/cart/items/{sku}` | none | 200 cart | 403 `csrf_invalid` |

- Lines are addressed by **SKU**, the identifier the catalogue API already exposes on each variant.
- `PUT` **sets** the quantity; it does not add to it. Sending the same request twice is harmless, which makes
  retries and double-clicks safe. Quantity `0` removes the line. `DELETE` of a missing line or unknown SKU is
  not an error.
- The cap is **10 per line**. Above that the server answers 400 `max`; it never silently clamps.

## Cart shape

```json
{
  "itemCount": 3,
  "subtotalMinor": 29970,
  "currency": "USD",
  "lines": [
    {
      "sku": "...", "productSlug": "...", "productName": "...", "brand": "...",
      "sizeLabel": "42", "colour": "black",
      "unitPriceMinor": 9990, "currency": "USD", "quantity": 3, "lineTotalMinor": 29970,
      "available": true, "maxQuantity": 10
    }
  ]
}
```

Money is integer minor units. Prices, names and availability are **live**: they come from the catalogue at
read time, so a price change shows up in the cart. The price a customer pays is fixed later, when checkout
snapshots it onto the order.

- `available` is false when the variant or product was deactivated, or stock is now below the quantity.
- `maxQuantity` is how high the quantity stepper may go right now (never above 10; 0 when unavailable).
- `subtotalMinor` counts **every** line, including unavailable ones. Availability is enforced at checkout.
- An empty cart is `{"itemCount":0,"subtotalMinor":0,"currency":"USD","lines":[]}`.

## States each screen needs

| Screen | States to design |
| --- | --- |
| Product page "Add to bag" | idle, adding (busy), added (confirmation, badge count updates), `insufficient_stock` ("only N left" style message owned by the design), out of stock (the catalogue's `available: false` on the variant), generic error |
| Header cart badge | no cart (0 or hidden), count, loading on first paint |
| Cart page / drawer | empty (with a way back to the catalogue), lines, quantity stepper at `maxQuantity`, line unavailable (stock fell or item withdrawn: keep it visible, explain, offer remove), price changed since added (compare with what the page last showed), updating a line (busy), remove (and undo, if the design wants it), generic error, proceeding to checkout |
| After sign-in | cart refreshes to the merged result; a one-line "we combined your bag" notice is a design choice |

## Known limits (recorded, not hidden)

- A cart does **not** reserve stock. Another buyer can take the last pair while it sits in a cart; checkout
  (slice 3d) is where stock is held, with an expiry.
- `insufficient_stock` is checked when the quantity is set. It can become stale afterwards; the line then shows
  `available: false`.
- Many simultaneous *first* writes from a brand-new guest (no cart yet) can each create a cart; the last cookie
  wins and the others are orphaned. A browser does not normally do this; a cleanup job for old guest carts is
  planned with the worker (phase 4).
- One currency (USD) in v1. Guest carts older than their cookie are not yet deleted from the database.
