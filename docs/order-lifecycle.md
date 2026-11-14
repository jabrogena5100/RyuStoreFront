# Order lifecycle

The rules live in one place: `OrderStatus` (`src/main/java/.../orders/OrderStatus.java`). Every status change
goes through `Orders.transition`, which locks the order row, applies those rules and writes a row to
`order_status_history`. The database only guarantees that a status is one of the six known values.

```
 PENDING --> PAID --> FULFILLED --> SHIPPED
    |          |          |            |
    v          v          v            v
CANCELLED   REFUNDED   REFUNDED     REFUNDED
```

| From | Allowed next | Who triggers it |
| --- | --- | --- |
| `pending` | `paid`, `cancelled` | `paid`: the Stripe webhook (phase 4.4). `cancelled`: a newer checkout (done), the expiry sweep, or a failed or expired payment (phase 4) |
| `paid` | `fulfilled`, `refunded` | `fulfilled`: admin (phase 5). `refunded`: admin refund |
| `fulfilled` | `shipped`, `refunded` | admin |
| `shipped` | `refunded` | admin |
| `cancelled`, `refunded` | nothing (terminal) | |

Rules worth knowing:
- A paid order is **never cancelled**; giving the money back is a refund.
- Nothing moves back to `pending`.
- Asking for the status an order already has is a **no-op** (`ALREADY`), not an error. That is what a replayed
  webhook looks like. Asking for a move the rules forbid returns `ILLEGAL` and changes nothing. A payment that
  arrives after the order was cancelled is `ILLEGAL` (`cancelled` to `paid`): the Stripe webhook (4.4b) handles it
  explicitly by recording the payment and refunding it (the order stays `cancelled`; there is no
  `cancelled` to `refunded` move), see `docs/payments.md`.
- `transition` returns a result (`APPLIED`, `ALREADY`, `ILLEGAL`, `NOT_FOUND`) instead of throwing. A thrown
  exception from a `@Transactional` method marks the caller's transaction rollback-only even if the caller
  catches it, which would silently roll back a whole webhook.

## Lock order (avoids deadlocks)

Anything that changes an order and its stock takes locks in this order: **the order row, then its
reservations, then variant rows**. `Orders.transition` is the first step. Checkout's "supersede the old
pending order" step does this: it cancels first, and releases stock only if the cancellation applied.
