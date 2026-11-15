# What the designed screens need from the API that does not exist yet

Written 2026-11-15 with the checkout, order and admin screens (`design/REVIEW.md`). The screens run on fixtures
(`web/src/lib/orders.ts`, `web/src/lib/catalog.ts`). **Nothing below is implemented or agreed**; it is the list the
frontend wiring (phase 5) has to settle with the backend. Existing contracts: `auth.md`, `cart.md`, `checkout.md`, `payment.md`.

| Need | Used by | Notes |
| --- | --- | --- |
| `GET /api/orders` (the signed-in customer's orders, newest first, paged) | Order history | Needs id, status, createdAt, totalMinor, item names and one product reference per order. |
| `GET /api/orders/{id}` | Order page, confirmation (polling until `paid`), pay step | Same shape as the checkout response, plus `history` (from `order_status_history`: status and timestamp). 404 for another customer's order, as for pay. |
| Order items carry `slug`, `brand`, `categorySlug` | Thumbnails and links in every order view | The checkout snapshot has only `productName`; add the product link and brand at snapshot time. |
| Price band parameter on catalogue search (`priceMax`, `priceMin`) and facet counts | Listing filters with counts | The listing's counts are computed on the fixtures; with the API they come from the search endpoint. |
| Admin: `GET /api/admin/orders` (status, text search, paging, customer name and e-mail) | Admin orders | 403 `forbidden` for a customer. |
| Admin: `POST /api/admin/orders/{id}/transition` (`fulfilled`, `shipped`, `refunded`) | Order actions | Goes through `Orders.transition`; `ILLEGAL` maps to 409 (the screen's "changed while you were looking"). Refund calls Stripe (idempotent). |
| Admin: product read and update, per-variant `stock` and `priceMinor` | Product edit | The public API only exposes `available`. Validation codes the screen shows: `required`, `min` (price), `negative` (stock). |
| Admin: presigned image upload | Photos | S3 presigned URL; the screen says files never pass through the shop server. |
| Low-stock threshold | Admin product list | Fixed at 12 pairs in the screen; a setting in real life. |
| Account menu state (`GET /api/auth/me`) | Header | The header shows a static "Sign in" link; the signed-in menu (name, orders, sign out) is not designed yet. |
