# 21st.dev components

Every component taken from 21st.dev is listed here: name, author, URL, where it goes, what was changed, and its licence as
stated on 21st.dev. Searches send only generic UI phrases (for example "checkout stepper progress steps"); no project name,
URL, secret or e-mail address.

**Status 2026-11-20 (phase 2): shortlist only.** Found with the free `search` tool. **No `get_component` call has been made,**
so no code has been retrieved and no licence has been read yet: search results carry no licence field. The proposal screens
in `design/proposal/` were written in this repo following each pattern; after approval the chosen components are retrieved
(paid quota), their licence recorded below, and the code adapted to the tokens (no default shadcn colours left).

## Shortlist (first choice per slot, then an alternative)

| Slot | Component (author) | URL | Where it goes | Planned changes | Licence |
| --- | --- | --- | --- | --- | --- |
| Sign in | Split Sign In Form (diarmuradi), id 39096 | https://21st.dev/@diarmuradi/components/sign-in-3 | `/sign-in` | Drop social sign-in (the API has none) and the testimonial (no customers to quote); a product image plus true account facts instead; dark tokens; keep the show/hide password toggle | not read yet |
| Sign up | Sign Up Split Panel (diarmuradi), id 28171 | https://21st.dev/@diarmuradi/components/sign-up-1 | `/register` | Same as sign in (same author, so the two pages match) | not read yet |
| Product card | ProductCard (youcefbnm), id 1635 | https://21st.dev/@youcefbnm/components/product-card | Listing, rows, related | Second image on hover as an opacity fade only; colour dots later if the API returns colours per product; no scale or tilt | not read yet |
| Product card (alt) | Product Card (deltacomponents), id 28197 | https://21st.dev/@deltacomponents/components/product-card | — | Compound card structure; drop the wishlist badge (no wishlist) | not read yet |
| Category grid | Shop by Category Grid (ziegfiroyt), id 29288 | https://21st.dev/@ziegfiroyt/components/ecommerce4 | Home "Shop by occasion" | Lead tile spans 2×2 on desktop; 3 % hover zoom; counts and "from" price in Geist Mono; no "featured" badges | not read yet |
| Product gallery | Thumbnail image viewer (ibrandify), id 37501 | https://21st.dev/@ibrandify/components/thumbnail-image-viewer | Product page | Keep the counter and arrow-key thumbnails; opacity swap; no location tags | not read yet |
| Gallery zoom | Zoomable Image (inference-sh), id 20027 | https://21st.dev/@inference-sh/components/zoomable-image | Product page, once photos exist | Click to enlarge; close on Escape (not on scroll); transform-only motion | not read yet |
| Search | Command Palette (vovalopuh13), id 46285 | https://21st.dev/@vovalopuh13/components/command-palette | Header search (⌘K / Ctrl+K and a button) | Results are products and occasions from the API; preview pane shows the product photo and price; remove the cursor tilt; keep combobox/listbox semantics | not read yet |
| Search (alt) | Command Palette (ebnsina), id 44074 | https://21st.dev/@ebnsina/components/command-palette | — | Simpler grouped list, no preview pane | not read yet |
| Cart drawer | Drawer (ddoemonn), id 23558 | https://21st.dev/@ddoemonn/components/drawer | Opens from the header cart and after "Add to cart" | Keep the focus trap, scroll lock and Escape; remove drag-to-dismiss on desktop; no backdrop blur | not read yet |
| Checkout steps | Stepper (kevingirelli), id 35237 | https://21st.dev/@kevingirelli/components/stepper | Checkout, pay, confirmation | Replace `ui/Steps.tsx`; complete/current/upcoming states with text, not colour alone | not read yet |
| Checkout steps (alt) | Steps (adit-firdaus), id 43786 | https://21st.dev/@adit-firdaus/components/steps | — | Drop the sliding thumb animation | not read yet |
| Hero filmstrip | Hero Carousel (crafterui), id 24752 | https://21st.dev/@crafterui/components/hero-carousel | Home hero | Keep the filmstrip with a lead frame; drop the background re-grade, wheel and drag hijacking; links in a native scroll-snap row on phones | not read yet |

Rejected after looking at the previews: tilt-and-glow product cards (glow is excluded by the brief), bento feature grids
(icons and tags, not images), animated-gradient sign-in panels (gradients are excluded), backdrop-blur drawers (glassmorphism).

## Retrieved components

None yet.

## Earlier use (2026-11-15)

Two components were read in phase D for their arrangement only, and none of their code is in the repository; see
`docs/decisions.md`, section "Components taken from 21st.dev".
