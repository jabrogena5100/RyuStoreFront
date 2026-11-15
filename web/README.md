# Storefront web

Next.js (App Router), TypeScript, Tailwind CSS with the design tokens, Motion for animation. Design phase: pages run on fixtures that mirror the API;
nothing calls the backend yet.

Needs only Docker (Node runs in a pinned image through `scripts/web.sh`, from the repo root):

```
scripts/web.sh npm install
scripts/web.sh npm run dev          # http://localhost:3000
scripts/web.sh npm run build
scripts/web.sh npx tsc --noEmit
scripts/design-shots.sh             # screenshots and accessibility scan, see design/README.md
```

Layout: `src/styles/tokens.css` (the only place colours, radii, type and motion are defined), `src/components/ui` (primitives), `src/components` (product, header, cart),
`src/lib/catalog.ts` (fixtures and filters), `src/app` (routes). `/design` shows every token and component state.
