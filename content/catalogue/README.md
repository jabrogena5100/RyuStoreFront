# Catalogue and product photos

The demo shop sells about 150 invented products, each built from real, licensed photographs. Image files never enter Git:
this folder holds the metadata, and scripts download and rebuild everything else.

## Rules

- **Licences allowed:** the Pexels License, the Pixabay Content License, CC0 and public domain. Not CC-BY or CC-BY-SA, not
  Unsplash, nothing from shops or brands. `search.py` and `fetch.py` both reject anything else.
- **Rejected at review:** a visible brand logo or wordmark (swoosh, stripes, N, star, printed names), an identifiable face,
  watermarks, collages, illustrations, heavy filters, shoes that are only a small part of the frame.
- **One product per photographer series:** a product's photos must show the same pair (same photographer, same shoot). A
  product may list a second colour only when the series shows it or the photo is ambiguous; the photo is never claimed to
  show a colour it does not.
- **Names, brands, prices and copy are invented** and say nothing the photo contradicts (a suede boot is not described as
  patent leather).

## Pipeline

| Step | Who | Command | Output |
| --- | --- | --- | --- |
| 1. Search | Owner (API keys) | `export PEXELS_API_KEY=… PIXABAY_API_KEY=…` then `python3 -I scripts/images/search.py` | `evidence-raw/images/candidates.json`, thumbnails, contact sheets (git-ignored) |
| 2. Review | Claude Code, owner spot-checks | read `evidence-raw/images/sheets/*.html` | `content/catalogue/selection.json` |
| 3. Fetch | Owner | `python3 -I scripts/images/fetch.py` | originals (git-ignored); `manifest.json`, `src/main/resources/catalogue/catalogue.json`, `web/src/data/image-credits.json` |
| 4. Variants | anyone (Docker) | `scripts/images/variants.sh` | AVIF + WebP at 480/960/1440 px (git-ignored); `docs/evidence/images-bytes.md` |
| 5. Upload | anyone (local) / owner (AWS) | `docker compose up -d seaweedfs` then `scripts/images/upload.sh local` | objects in `storefront-images` |
| 6. Seed | anyone | `docker compose down -v && docker compose up -d`, then the API with the `seed` profile | products, variants, `product_images` rows |

Rebuilding from zero on a new machine needs no API key: `python3 -I scripts/images/fetch.py --from-manifest` downloads exactly
the files in `manifest.json` and refuses any whose SHA-256 changed, then steps 4 to 6.

## Files

- `queries.json`: search phrases per shop category (generic words, no brand names).
- `selection.json`: the review result, one entry per product:
  `{ "slug", "name", "brand", "category", "gender", "colours": [...], "priceDollars", "description", "photos": [{ "ref": "C0123", "alt": "..." }] }`.
  `ref` is the candidate number printed on the contact sheet; `alt` describes the photo for screen readers.
- `manifest.json`: one entry per photo (source page, author, licence name and URL, date retrieved, measured width and height,
  bytes, SHA-256, download URL). The `/credits` page lists the same data.

Object keys are `products/<slug>-<n>-<first 8 hex of the SHA-256>`, so a replaced photo gets a new key and served files can be
cached as immutable.
