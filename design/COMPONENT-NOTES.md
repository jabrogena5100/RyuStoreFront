# 21st.dev components

Every component taken from 21st.dev is listed here: name, author, URL, where it goes, what was changed, and its licence as
stated on 21st.dev. Searches send only generic UI phrases (for example "checkout stepper progress steps"); no project name,
URL, secret or e-mail address.

**Status 2026-11-20 (phase 3): 9 retrieved with `get_component`, 7 used, 2 dropped for having no licence.**
`get_component` results carry no licence field, so each component's 21st.dev page was read for its "License" value
(2026-11-20). 21st.dev's terms (https://21st.dev/terms, last updated July 20, 2026) ask that the original licence be kept
and the author credited, and that copied components link back to their page; every adapted file carries a header with the
author, the component URL and "MIT", and this file links back.

What "adapted" means here: no 21st.dev file was copied in whole. Each was ported to native React and CSS on the design
tokens with **no new runtime dependency** (the originals pull in framer-motion/motion, Radix, base-ui,
class-variance-authority or react-medium-image-zoom). Kept and dropped parts are listed per component below.

## Used (MIT, as shown on each page)

| Component (author, library) | URL | File | Kept | Changed or dropped |
| --- | --- | --- | --- | --- |
| ProductCard (youcefbnm, Systaliko UI) | https://21st.dev/@youcefbnm/components/product-card | `web/src/components/ProductCard.tsx` | Second image fading in over the first on hover | motion library → CSS opacity 180 ms; also on keyboard focus; no colour dots (no per-product colours in the API) |
| Shop by Category Grid (ziegfiroyt, Beste) | https://21st.dev/@ziegfiroyt/components/ecommerce4 | `web/src/components/CategoryGrid.tsx` | Image cards, bottom scrim, name/description/count over the image, hover zoom, arrow nudge | Lead tile 2×2; zoom 3 % over 240 ms (was 5 % over 500 ms); mono counts and "from" prices; no "Featured" badge; no cva |
| Thumbnail image viewer (ibrandify, ibrandify UI) | https://21st.dev/@ibrandify/components/thumbnail-image-viewer | `web/src/components/Gallery.tsx` | `01 / 04` counter, previous/next on the stage, tablist with roving tabindex, Left/Right/Home/End | 4:5 frame; opacity swap 180 ms; no captions, location tags or backdrop blur |
| Command Palette (vovalopuh13) | https://21st.dev/@vovalopuh13/components/command-palette | `web/src/components/CommandPalette.tsx` | Fuzzy subsequence match and scoring, underlined matched letters, groups, combobox/listbox with `aria-activedescendant`, Up/Down/Home/End/Enter/Esc, preview pane, result count, key hints | Native `<dialog>` instead of a hand-built overlay; framer-motion springs, cursor glow, 3D tilt, amber gradient and backdrop blur removed (180 ms fade); results from `GET /api/products` plus occasions and quick links |
| Drawer (ddoemonn, interior.dev) | https://21st.dev/@ddoemonn/components/drawer | `web/src/components/CartDrawer.tsx` | Focus in on open and back on close, Tab trap, inert background, scroll lock, Escape, scrim click | motion spring → 240 ms CSS transform; drag-to-dismiss dropped; content is the cart |
| Stepper (kevingirelli, @delego) | https://21st.dev/@kevingirelli/components/stepper | `web/src/components/ui/Steps.tsx` | `<nav>` + ordered list, `aria-current="step"`, state read after each number, filled connector, only the current title on phones | No context or clickable triggers (steps are pages); no cva |
| Hero Carousel (crafterui) | https://21st.dev/@crafterui/components/hero-carousel | `web/src/components/HeroFilmstrip.tsx` | Oversized headline on a filmstrip sharing one top edge, a larger lead frame, numbered mono labels | No carousel state, background re-grade, film grain, wheel capture, drag or autoplay; frames are product links; scroll-snap on phones |

## Retrieved, not used yet

| Component | URL | Licence | Why not yet |
| --- | --- | --- | --- |
| Zoomable Image (inference-sh) | https://21st.dev/@inference-sh/components/zoomable-image | MIT | Wraps a real `<img>` with `react-medium-image-zoom`; there are no photos until phase 4. Decide then whether the dependency is worth its weight. |

## Retrieved and dropped (no licence)

| Component | URL | Licence shown | What happened |
| --- | --- | --- | --- |
| Split Sign In Form (diarmuradi) | https://21st.dev/@diarmuradi/components/sign-in-3 | "no-license" | None of its code is in the repository. The split sign-in layout in `AuthShell.tsx` and `PasswordField.tsx` was written here (phase 2) before retrieval; a split screen and a show/hide toggle are common patterns, not this component's code. |
| Sign Up Split Panel (diarmuradi) | https://21st.dev/@diarmuradi/components/sign-up-1 | "no-license" | Same as above. |

## Shortlist as approved (2026-11-20)

| Slot | Component (author) | URL | Where it goes | Planned changes | Licence |
| --- | --- | --- | --- | --- | --- |
| Sign in | Split Sign In Form (diarmuradi), id 39096 | https://21st.dev/@diarmuradi/components/sign-in-3 | `/sign-in` | Drop social sign-in (the API has none) and the testimonial (no customers to quote); a product image plus true account facts instead; dark tokens; keep the show/hide password toggle | see the tables above |
| Sign up | Sign Up Split Panel (diarmuradi), id 28171 | https://21st.dev/@diarmuradi/components/sign-up-1 | `/register` | Same as sign in (same author, so the two pages match) | see the tables above |
| Product card | ProductCard (youcefbnm), id 1635 | https://21st.dev/@youcefbnm/components/product-card | Listing, rows, related | Second image on hover as an opacity fade only; colour dots later if the API returns colours per product; no scale or tilt | see the tables above |
| Product card (alt) | Product Card (deltacomponents), id 28197 | https://21st.dev/@deltacomponents/components/product-card | — | Compound card structure; drop the wishlist badge (no wishlist) | see the tables above |
| Category grid | Shop by Category Grid (ziegfiroyt), id 29288 | https://21st.dev/@ziegfiroyt/components/ecommerce4 | Home "Shop by occasion" | Lead tile spans 2×2 on desktop; 3 % hover zoom; counts and "from" price in Geist Mono; no "featured" badges | see the tables above |
| Product gallery | Thumbnail image viewer (ibrandify), id 37501 | https://21st.dev/@ibrandify/components/thumbnail-image-viewer | Product page | Keep the counter and arrow-key thumbnails; opacity swap; no location tags | see the tables above |
| Gallery zoom | Zoomable Image (inference-sh), id 20027 | https://21st.dev/@inference-sh/components/zoomable-image | Product page, once photos exist | Click to enlarge; close on Escape (not on scroll); transform-only motion | see the tables above |
| Search | Command Palette (vovalopuh13), id 46285 | https://21st.dev/@vovalopuh13/components/command-palette | Header search (⌘K / Ctrl+K and a button) | Results are products and occasions from the API; preview pane shows the product photo and price; remove the cursor tilt; keep combobox/listbox semantics | see the tables above |
| Search (alt) | Command Palette (ebnsina), id 44074 | https://21st.dev/@ebnsina/components/command-palette | — | Simpler grouped list, no preview pane | see the tables above |
| Cart drawer | Drawer (ddoemonn), id 23558 | https://21st.dev/@ddoemonn/components/drawer | Opens from the header cart and after "Add to cart" | Keep the focus trap, scroll lock and Escape; remove drag-to-dismiss on desktop; no backdrop blur | see the tables above |
| Checkout steps | Stepper (kevingirelli), id 35237 | https://21st.dev/@kevingirelli/components/stepper | Checkout, pay, confirmation | Replace `ui/Steps.tsx`; complete/current/upcoming states with text, not colour alone | see the tables above |
| Checkout steps (alt) | Steps (adit-firdaus), id 43786 | https://21st.dev/@adit-firdaus/components/steps | — | Drop the sliding thumb animation | see the tables above |
| Hero filmstrip | Hero Carousel (crafterui), id 24752 | https://21st.dev/@crafterui/components/hero-carousel | Home hero | Keep the filmstrip with a lead frame; drop the background re-grade, wheel and drag hijacking; links in a native scroll-snap row on phones | see the tables above |

Rejected after looking at the previews: tilt-and-glow product cards (glow is excluded by the brief), bento feature grids
(icons and tags, not images), animated-gradient sign-in panels (gradients are excluded), backdrop-blur drawers (glassmorphism).

## Earlier use (2026-11-15)

Two components were read in phase D for their arrangement only, and none of their code is in the repository; see
`docs/decisions.md`, section "Components taken from 21st.dev".
