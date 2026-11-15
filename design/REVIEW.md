# Review gallery: layout and motion revision, then checkout, orders and admin

Branch `design/layout-and-calm-motion`, 2026-11-15. Screenshots are Chromium at 1440 px and 390 px wide, made by `scripts/design-shots.sh` from the production build; the axe scan found 0 violations on all 117 pages (33 before batch 2). **Owner sign-off: not done.** What was not checked is listed at the end.

What changed everywhere: two-tier header and a four-column footer on every page; one container width and one section heading style; minimal motion (colour and opacity only, no scroll-reveal, shared-layout, exit or hover-zoom animations, no shimmer, toast by CSS, the `motion` dependency removed). Reasons and trade-offs are in [docs/decisions.md](../docs/decisions.md).

## Home

Before: bespoke uneven occasion grid (one large tile spanning two rows), a single-tier sticky header with a menu dialog, one product row, scroll-reveal animations. After: two-tier header, hero with the finder card kept, five equal tiles, two product rows (New in, Under $150), a value strip of three true statements, a four-column footer.

| Before (desktop) | After (desktop) |
| --- | --- |
| ![](review-before/home-desktop.png) | ![](screens/home-desktop.png) |

| Before (phone) | After (phone) |
| --- | --- |
| ![](review-before/home-mobile.png) | ![](screens/home-mobile.png) |

## Listing

Before: three rows of filter chips and a collapsible size panel above the grid, no counts. After: filter sidebar on desktop with a count beside every option, active-filter pills with "Clear all", sort at the top right, four-column grid.

| Before (desktop) | After (desktop) |
| --- | --- |
| ![](review-before/listing-desktop.png) | ![](screens/listing-desktop.png) |

| Before (phone) | After (phone) |
| --- | --- |
| ![](review-before/listing-mobile.png) | ![](screens/listing-mobile.png) |

## Product page

Before: own container and heading sizes, animated image cross-fade and a sliding size-selection indicator. After: same content on the shared container and section heading ("More in ..." uses the shared `Section`), no animation; selected size is a filled state.

| Before (desktop) | After (desktop) |
| --- | --- |
| ![](review-before/pdp-desktop.png) | ![](screens/pdp-desktop.png) |

| Before (phone) | After (phone) |
| --- | --- |
| ![](review-before/pdp-mobile.png) | ![](screens/pdp-mobile.png) |

## Cart

Before: lines animated in and out. After: same content and layout on the shared container, header and footer; lines change instantly.

| Before (desktop) | After (desktop) |
| --- | --- |
| ![](review-before/cart-filled-desktop.png) | ![](screens/cart-filled-desktop.png) |

| Before (phone) | After (phone) |
| --- | --- |
| ![](review-before/cart-filled-mobile.png) | ![](screens/cart-filled-mobile.png) |

## New and changed states (no before)

| Screen | Desktop | Phone |
| --- | --- | --- |
| Listing, filtered (After dark, women) | ![](screens/listing-night-desktop.png) | ![](screens/listing-night-mobile.png) |
| Listing, price band plus in stock | ![](screens/listing-price-desktop.png) | ![](screens/listing-price-mobile.png) |
| Listing, phone filter drawer | | ![](screens/listing-drawer-mobile.png) |
| Listing, loading | ![](screens/listing-loading-desktop.png) | ![](screens/listing-loading-mobile.png) |
| Listing, empty | ![](screens/listing-empty-desktop.png) | ![](screens/listing-empty-mobile.png) |
| Listing, error | ![](screens/listing-error-desktop.png) | ![](screens/listing-error-mobile.png) |
| Product, sold out | ![](screens/pdp-soldout-desktop.png) | ![](screens/pdp-soldout-mobile.png) |
| Product, after add to cart | ![](screens/pdp-added-desktop.png) | ![](screens/pdp-added-mobile.png) |
| Cart, empty | ![](screens/cart-empty-desktop.png) | ![](screens/cart-empty-mobile.png) |
| Cart, loading | ![](screens/cart-loading-desktop.png) | ![](screens/cart-loading-mobile.png) |
| Cart, error | ![](screens/cart-error-desktop.png) | ![](screens/cart-error-mobile.png) |
| Design system page | ![](screens/design-desktop.png) | ![](screens/design-mobile.png) |
| Not found | ![](screens/not-found-desktop.png) | ![](screens/not-found-mobile.png) |

## Batch 2: checkout, orders, sign in and admin (no before; all new)

Every screen below shares the header, container, cards, fields and tokens of batch 1. Admin has its own bar and navigation. All data are fixtures; contracts that do not exist yet are in [docs/api/proposed-ui-needs.md](../docs/api/proposed-ui-needs.md). The axe scan found 0 violations on all 117 pages.

| Screen | Desktop | Phone |
| --- | --- | --- |
| Sign in | ![](screens/sign-in-desktop.png) | ![](screens/sign-in-mobile.png) |
| Sign in, wrong credentials, from checkout | ![](screens/sign-in-invalid-desktop.png) | ![](screens/sign-in-invalid-mobile.png) |
| Create account | ![](screens/register-desktop.png) | ![](screens/register-mobile.png) |
| Create account, e-mail taken | ![](screens/register-taken-desktop.png) | ![](screens/register-taken-mobile.png) |
| Create account, submitting | ![](screens/register-submitting-desktop.png) | ![](screens/register-submitting-mobile.png) |
| Checkout | ![](screens/checkout-desktop.png) | ![](screens/checkout-mobile.png) |
| Checkout, field errors | ![](screens/checkout-errors-desktop.png) | ![](screens/checkout-errors-mobile.png) |
| Checkout, submitting | ![](screens/checkout-submitting-desktop.png) | ![](screens/checkout-submitting-mobile.png) |
| Checkout, failed (retry) | ![](screens/checkout-failed-desktop.png) | ![](screens/checkout-failed-mobile.png) |
| Checkout, cart changed | ![](screens/checkout-changed-desktop.png) | ![](screens/checkout-changed-mobile.png) |
| Checkout, signed out | ![](screens/checkout-signedout-desktop.png) | ![](screens/checkout-signedout-mobile.png) |
| Checkout, empty | ![](screens/checkout-empty-desktop.png) | ![](screens/checkout-empty-mobile.png) |
| Pay step | ![](screens/pay-desktop.png) | ![](screens/pay-mobile.png) |
| Pay step, starting | ![](screens/pay-starting-desktop.png) | ![](screens/pay-starting-mobile.png) |
| Pay step, Stripe error | ![](screens/pay-provider-error-desktop.png) | ![](screens/pay-provider-error-mobile.png) |
| Pay step, expired | ![](screens/pay-expired-desktop.png) | ![](screens/pay-expired-mobile.png) |
| Confirmation, paid | ![](screens/confirmation-desktop.png) | ![](screens/confirmation-mobile.png) |
| Confirmation, confirming | ![](screens/confirmation-confirming-desktop.png) | ![](screens/confirmation-confirming-mobile.png) |
| Confirmation, cancelled | ![](screens/confirmation-cancelled-desktop.png) | ![](screens/confirmation-cancelled-mobile.png) |
| Confirmation, refunded | ![](screens/confirmation-refunded-desktop.png) | ![](screens/confirmation-refunded-mobile.png) |
| Order history | ![](screens/orders-desktop.png) | ![](screens/orders-mobile.png) |
| Order history, empty | ![](screens/orders-empty-desktop.png) | ![](screens/orders-empty-mobile.png) |
| Order history, loading | ![](screens/orders-loading-desktop.png) | ![](screens/orders-loading-mobile.png) |
| Order history, error | ![](screens/orders-error-desktop.png) | ![](screens/orders-error-mobile.png) |
| Order history, signed out | ![](screens/orders-signedout-desktop.png) | ![](screens/orders-signedout-mobile.png) |
| Order page, shipped | ![](screens/order-shipped-desktop.png) | ![](screens/order-shipped-mobile.png) |
| Order page, awaiting payment | ![](screens/order-pending-desktop.png) | ![](screens/order-pending-mobile.png) |
| Admin orders | ![](screens/admin-orders-desktop.png) | ![](screens/admin-orders-mobile.png) |
| Admin orders, filtered (paid) | ![](screens/admin-orders-paid-desktop.png) | ![](screens/admin-orders-paid-mobile.png) |
| Admin orders, empty | ![](screens/admin-orders-empty-desktop.png) | ![](screens/admin-orders-empty-mobile.png) |
| Admin orders, loading | ![](screens/admin-orders-loading-desktop.png) | ![](screens/admin-orders-loading-mobile.png) |
| Admin orders, error | ![](screens/admin-orders-error-desktop.png) | ![](screens/admin-orders-error-mobile.png) |
| Admin orders, not allowed | ![](screens/admin-orders-forbidden-desktop.png) | ![](screens/admin-orders-forbidden-mobile.png) |
| Admin order, paid (pack or refund) | ![](screens/admin-order-paid-desktop.png) | ![](screens/admin-order-paid-mobile.png) |
| Admin order, shipped (refund only) | ![](screens/admin-order-shipped-desktop.png) | ![](screens/admin-order-shipped-mobile.png) |
| Admin order, changed meanwhile | ![](screens/admin-order-conflict-desktop.png) | ![](screens/admin-order-conflict-mobile.png) |
| Admin order, refund dialog | ![](screens/admin-order-refund-desktop.png) | ![](screens/admin-order-refund-mobile.png) |
| Admin products | ![](screens/admin-products-desktop.png) | ![](screens/admin-products-mobile.png) |
| Admin products, low stock | ![](screens/admin-products-low-desktop.png) | ![](screens/admin-products-low-mobile.png) |
| Admin product edit | ![](screens/admin-product-desktop.png) | ![](screens/admin-product-mobile.png) |
| Admin product edit, errors | ![](screens/admin-product-errors-desktop.png) | ![](screens/admin-product-errors-mobile.png) |
| Admin product edit, saved | ![](screens/admin-product-saved-desktop.png) | ![](screens/admin-product-saved-mobile.png) |

## Things to look at

- The five category tiles use the first product of each category as their picture; the placeholders are still typographic.
- Kai Slide (sandals) belongs to no occasion, so it appears only under "All shoes" and the price filters. Decide whether sandals need a tile.
- Batch 2: the order of checkout fields, whether the pay step should show the address again, and whether admin should keep its own bar or reuse the shop header.
- Batch 2: statuses are worded for shoppers ("Awaiting payment", "Packed") while the database says `pending` and `fulfilled`; say if you prefer the raw names.
- The header is not sticky. Say if you want it to stay on screen.
- The phone category tile row ends with one full-width tile (five tiles in two columns).

## Not checked

Typing into any form or submitting it (batch 2 forms were only seen as screenshots); the refund dialog by keyboard; real devices; Firefox and WebKit; keyboard-only and screen-reader walkthroughs; Lighthouse; touch behaviour of the drawer and the product row; the filter links by clicking through every option; anything against the real API (the data are fixtures).
