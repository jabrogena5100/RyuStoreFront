# Review gallery: layout and motion revision

Branch `design/layout-and-calm-motion`, 2026-11-15. Screenshots are Chromium at 1440 px and 390 px wide, made by `scripts/design-shots.sh` from the production build; the axe scan found 0 violations on all 33 pages. **Owner sign-off: not done.** What was not checked is listed at the end.

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

## Things to look at

- The five category tiles use the first product of each category as their picture; the placeholders are still typographic.
- Kai Slide (sandals) belongs to no occasion, so it appears only under "All shoes" and the price filters. Decide whether sandals need a tile.
- The header is not sticky. Say if you want it to stay on screen.
- The phone category tile row ends with one full-width tile (five tiles in two columns).

## Not checked

Real devices; Firefox and WebKit; keyboard-only and screen-reader walkthroughs; Lighthouse; touch behaviour of the drawer and the product row; the filter links by clicking through every option; anything against the real API (the data are fixtures).
