# Design brief

Status: draft v3 (2026-11-15). Layout and motion revised after the owner reviewed the first screens (sections 3, 6 and 8a). The owner has answered the open questions. Design-in-code (section 1) is treated as approved because the
owner answered every question and asked for the work to go ahead; they can still reverse it. Nothing here is built or measured yet.

## 1. How design gets done (and why not Claude Design or an MCP server)

CLAUDE.md section 6 assumed Claude Design. This project **designs in code instead** (a recorded deviation from CLAUDE.md, see `docs/decisions.md`).

- The design system is a tokens file (CSS variables) plus components either way. Building it directly in the app removes the export and hand-off step.
- Claude Code can look at screenshots (Playwright is already planned for end-to-end tests), compare them with the brief, and iterate. The owner reviews every screen.
- Everything stays in the repo under `design/` (tokens, component gallery, screen screenshots, decisions).
- An MCP server is a connector (to Figma, a browser, a database), not a designer. None is set up for this project; the one that is configured (`aws-mcp`) fails to start (`uvx` not found). A Figma or browser MCP could be added later as a *reviewer* tool, not a requirement.
- **What is lost:** a visual canvas to drag things around, and a second tool's opinions. **The risk:** a generic look. The mitigation is this brief, the anti-slop checklist (section 9), real seed data on every screen, and the owner rejecting anything that looks templated.

## 2. The product and audience

- Footwear: women's and men's; luxury, chunky trend, clean white, boots, carbon-plate runners. Invented brand names only (no real trademarks); imagery must be licensed or self-made.
- Dark only for v1 (owner decision). White and light shoes should look like objects on a gallery wall.
- **Buyer (owner's words, condensed):** people who want quick access to the footwear market, whether that is white shoes, streetwear sneakers,
  carbon-plate running shoes, evening and night-out shoes, women's boots and so on.
- **What that means for the design:** speed and breadth. People arrive with an occasion or a type in mind, not a brand. So: browse by occasion and type from the first screen,
  filters that are one tap, few steps from product to paid, fast pages. Quickness is part of the brand and a performance target, not just a word.

## 3. Brand: three adjectives (proposal, change freely)

**Quick, restrained, confident.** Quick: you find the shoe and pay in a few taps; nothing makes you wait. Restrained: few colours, calm space, no decoration for its own sake.
Confident: short copy and clear type, nothing apologetic. (Proposal; change freely.)

**What to avoid (owner's dislikes, each with what it means here):**
- *Amazon: looks obsolete.* No dense, cluttered grids, no walls of badges and ratings, no ad-like blocks, no dated form styling.
- *Adidas: too formulaic.* Revised 2026-11-15 after the owner reviewed the first screens: the owner now prefers the familiar, widely used shop layout (section 8a) as long as it is applied consistently and does not look generated. The first draft's bespoke, uneven occasion grid was replaced by equal tiles. What still applies: no banners, no ad-like blocks, no claim the data cannot back.
- *Race-car sites: huge layouts and loud, racy colours.* No oversized type that fills the screen, no saturated reds and yellows, no speed lines, italic slants or diagonal cuts. Quick means calm and efficient, not fast-looking.

Positive references: none given yet. Not required to start; add two or three sites and what you like about them if you find some, and the system will be checked against them.

## 4. Colour: black and grey that works on screens

Principles:
- **Not pure black, not pure white.** A near-black canvas with a very faint warm tint avoids the harsh, smeary look of `#000`, and off-white text avoids glare against it.
- **Depth comes from lightness, not shadows.** Raised surfaces are slightly lighter, in even perceptual steps (OKLCH lightness). Shadows barely show on dark backgrounds.
- **Almost no hue.** Chroma stays under 0.005 for neutrals; the shoes carry the colour. Primary buttons are light-on-dark (off-white fill, near-black text).
- **Semantic colours** (success, warning, danger) are desaturated and lighter than on a light theme, checked for contrast on every surface.

Starting values (computed in this session with a script; contrast is WCAG on the stated surfaces). To be tuned by eye, then frozen in the tokens file:

| Token | OKLCH | Hex | Use |
| --- | --- | --- | --- |
| canvas | 0.145 0.004 80 | `#0B0A08` | page background |
| surface-1 | 0.185 0.004 80 | `#141211` | cards, header |
| surface-2 | 0.225 0.004 80 | `#1D1C1A` | inputs, raised panels |
| surface-3 | 0.27 0.004 80 | `#272624` | hover, selected |
| line | 0.32 0.004 80 | `#343331` | decorative dividers (not required to meet contrast) |
| line-strong | 0.545 0.004 80 | `#71706D` | input and control borders: at least 3.05:1 on every surface |
| text-subtle | 0.645 0.004 80 | `#8F8D8B` | captions, placeholders: at least 4.57:1 on every surface |
| text-muted | 0.72 0.004 80 | `#A6A4A2` | secondary text: 8.0:1 on canvas |
| text | 0.93 0.003 80 | `#E9E8E6` | body: 16.2:1 on canvas |
| text-strong | 0.985 0.002 80 | `#FBFAF9` | headings, primary button fill |

Corrections already made: an earlier draft had `text-subtle` at 4.3:1 on `surface-2` and borders near 2:1, both below WCAG AA; the values above fix that.

**Accent (owner decision): one good blue.** A calm, saturated cobalt, not an electric one (hue 258 in OKLCH, chosen for a premium rather than a racy feel). Used sparingly: the primary action, links, focus rings, the selected size or colour, the active filter. Everything else stays neutral.

| Token | OKLCH | Hex | Contrast (computed) |
| --- | --- | --- | --- |
| accent | 0.56 0.19 258 | `#1A6FE1` | with an off-white label 4.6:1; against the canvas 4.2:1 (at least 3:1 for a UI boundary) |
| accent-text | 0.65 0.13 258 | `#5B90DD` | on canvas 6.1:1; on the lightest surface 4.7:1 (links, focus, small accents) |

Primary buttons are the blue fill with off-white text; secondary buttons are neutral. Semantic colours (success, warning, danger) must stay clearly different from the blue.
**Dark only for v1** (owner decision); the tokens are still structured so a light theme can be added later.

Imagery (owner decision): **consistent placeholders first.** The seed data has no images. Placeholders are one fixed aspect ratio (4:5), tone-on-tone on the dark surfaces with a soft light,
and an oversized initial of the model name cropped by the frame. They differ per product and per gallery image, so grids are not monotone, and they do not pretend to be photos.
They share one component and one ratio, so real photos drop in later without layout changes.
*Revised after review:* the first version drew a line silhouette of each shoe type. Rendered at real size it read as slippers and looked like a failed photo, and a row of one type showed identical drawings, so it was dropped for the typographic version.
Where real images could come from is in the working notes, not here.

## 5. Type

One variable sans (UI and headlines). **Chosen: Instrument Sans**, loaded and self-hosted through `next/font` (Google Fonts, open licence; the exact licence text is still to be confirmed before launch). Prices use tabular numbers.
Large, tight display sizes for headlines; a fluid scale with `clamp()`. Candidates to try side by side with real product names: Geist, Instrument Sans, Manrope. The licence of whichever is chosen gets checked before use.

## 6. Motion (animations): minimal

Revised 2026-11-15 at the owner's request: the first screens had scroll-reveal, shared-layout and exit animations, hover zoom and a skeleton shimmer; all removed. Rules now:
- **Colour and opacity changes only**, on hover, focus and press, 100 to 200 ms, one easing. Nothing moves, scales or reveals on scroll. No shimmer; skeletons are still blocks.
- Motion tokens: durations 100 / 150 / 200 ms (fast, base, slow) and one easing (standard). No other values in components.
- The only keyframe is the toast's 150 ms fade-in (CSS); it leaves without an exit animation.
- `prefers-reduced-motion` makes every transition and animation instant, and the product row's arrow buttons jump instead of scrolling smoothly.
- The `motion` package is no longer a dependency.
- Never block interaction on an animation. Measure Lighthouse performance and INP on the product page; no performance claim until measured.

## 7. Responsiveness on all platforms

- Mobile-first, then up. Container queries for components, `clamp()` for type and spacing, `dvh` units and safe-area insets for phones with notches and browser bars.
- Tap targets at least 44 px; no hover-only affordances; visible focus states; keyboard operable.
- Support policy (owner confirmed): current and previous major versions of Chrome, Edge, Firefox and Safari, plus iOS Safari and Android Chrome.
- Test matrix in Playwright: Chromium, Firefox and WebKit at 360, 390, 768, 1024 and 1440 px wide, plus phone emulation. **Emulation is not a real device: real-device checks are not planned yet.**

## 8. Stack recommendation

Next.js (App Router) and TypeScript, as in CLAUDE.md, with:
- **Tailwind CSS** with the tokens as CSS variables (one source of truth, components use only tokens).
- **Native HTML first** (revised during the build): `<dialog>` for the menu, `<details>` for accordions, radio inputs for the size picker, a native `<select>` for sort. They give focus handling, keyboard support and screen-reader semantics for free and add no dependency. Radix/shadcn is added only if a component needs something native HTML cannot do.
- **No animation library** (revised 2026-11-15, section 6): plain CSS transitions for colour and opacity.
- `next/image` for responsive images (CloudFront later), `next/font` for fonts, Playwright and axe for tests.
- Versions pinned 2026-11-15 after checking the npm registry and Docker Hub: Node 26.13.1 (LTS) in Docker, Next.js 16.4.6, React 19.3.1, Tailwind CSS 4.3.5, Motion 14.14.0, TypeScript 7.0.2 (Motion 14.14.0 was pinned and has been removed), lucide-react 1.73.0, Playwright 1.65.1, axe-core for Playwright 4.14.0. See `web/package.json`.

Considered and not recommended for v1: SvelteKit (excellent built-in animation, but CLAUDE.md commits to Next.js and a switch costs the ecosystem), Astro (great for content, awkward for a cart-heavy app), a WebGL/3D shoe viewer (a lovely optional later feature; expensive in performance and effort, so lazy-loaded if ever).
The API is on its own subdomain (`api.ryustorefront.com`, see `docs/decisions.md`): the frontend should proxy `/api` through Next.js server-side, or CORS with credentials is needed. Settle before phase 5.

## 8a. Layout system (2026-11-15)

One familiar shop layout, applied everywhere. Container: `max-w-7xl` with 16/24/32 px gutters (`Container`). Section: one heading size (`text-h2`), 48/64 px gap above (`Section`). Cards: `ProductCard` for products, rounded border cards for tiles and panels.

- **Header, two tiers:** logo, prominent search with a button, cart with count; below it a category bar (All shoes, the five occasions, then Women, Men, New in, Under $150). Not sticky. On phones the search drops to its own row and the category bar scrolls sideways.
- **Home:** hero (headline plus the quick finder card), equal-size category tiles, product rows ("New in" is the newest by id; "Under $150" is the products priced below $150; no "best seller" or popularity claims, the data has none), a value strip with three statements the product can back (flat $8 shipping, Stripe test mode, US sizes with the sizes still available shown), a four-column footer.
- **Listing:** filter sidebar on desktop (occasion, who it is for, price, size, colour, availability, each option with the number of pairs it would show), a filter drawer on phones, active-filter pills above the grid with "Clear all", sort at the top right. Filters are links to URLs: server-rendered, shareable, no client state.
- **Product page and cart:** unchanged in content, moved onto the same container, section heading and card style.

## 9. Screens, states and the anti-slop checklist

Screens, each in desktop and mobile with **empty, loading and error states**: home, listing with filters, product detail, cart, checkout, order confirmation, order history, admin product edit, admin orders.
Use real product names, prices and variants from the seed data and the contracts in `docs/api/`.

Review every screen against:
- No lorem ipsum, no "Welcome to our store", no centred-tagline gradient hero.
- One icon set; no emoji as icons.
- Spacing only from the scale; radii and elevation only from tokens.
- Contrast AA (text 4.5:1, UI boundaries 3:1), visible focus, 44 px targets.
- Copy written for footwear, not generic retail.
- Dark-specific: no pure `#000` or `#fff`, no grey-on-grey text below AA, no glowing neon, shadows replaced by surface lightness.

## 10. Process and exit test (phase D)

1. Owner approves or edits this brief (answers in sections 2, 3, 4 and 7 are in).
2. Tokens file and a component gallery page (buttons, inputs, cards, price, badge, nav, toast, empty and error states).
3. Screens one by one with real data; Playwright screenshots saved under `design/screens/`; the owner signs off each.
4. Exit test (CLAUDE.md phase D): tokens file and screen exports committed under `design/`; the owner signs off each screen. Later (phase 5): axe scan in CI, Lighthouse numbers recorded, design-versus-build screenshots.
