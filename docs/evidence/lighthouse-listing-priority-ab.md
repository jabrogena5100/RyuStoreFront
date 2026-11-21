# Lighthouse A/B: listing-priority-ab

- Lighthouse 13.6.0, HeadlessChrome/159.0.0.0, run 2026-11-21
- **Before:** commit `0e1f1ef`. **After:** `2753d57 + uncommitted changes in web/`. Both production builds (`next build` + `next start`, ports 3001 and 3000), served at the same time.
- Both against the same running API at `http://localhost:8080` (the real Spring API with the seed profile, Postgres and SeaweedFS in Compose). Product page: `/products/ostrava-atelier-umber-chelsea-boot`.
- Machine: RyuStoreFront, 4 CPUs. Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.
- Runs interleaved: for each run, page and form factor, before and after back to back, alternating which goes first. Page weight = transferred bytes (compressed).
- Raw reports: `evidence-raw/lighthouse/listing-priority-ab-before/` and `-after/` (git-ignored).

## Medians (min–max)

| Page | Form factor | Build | Runs | Perf | A11y | BP | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| listing | desktop | before | 5 | 92 (90–96) | 100 | 96 | 339 (332–358) | 339 (332–358) | 236 (173–258) | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | after | 5 | 97 (95–98) | 100 | 100 | 327 (305–332) | 729 (684–1013) | 144 (92–182) | 0.000 | 560.3 (559.1–560.3) | 155.3 | 254.2 | 89 (87–89) |
| listing | mobile | before | 5 | 72 (72–74) | 100 | 96 | 950 (947–1205) | 950 (947–1205) | 1942 (1499–2118) | 0.000 | 273.9 (273.3–273.9) | 155.0 | 0.0 | 34 (33–34) |
| listing | mobile | after | 5 | 73 (60–75) | 100 | 100 | 1073 (927–1157) | 2702 (2299–4071) | 1142 (808–1711) | 0.000 | 525.1 (524.5–525.1) | 155.3 | 254.2 | 60 (59–60) |

## Paired differences (after − before, same run number)

Median of the per-pair differences, then how many pairs got better / worse / stayed equal. A direction is only worth
quoting when (nearly) all pairs agree; a split means the runs cannot tell the builds apart.

| Page | Form factor | Pairs | Perf | LCP ms | TBT ms | CLS | Total KB | Img KB | BP |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| listing | desktop | 5 | +6 (4↑ 1↓ 0=) | +376 (0↑ 5↓ 0=) | -114 (4↑ 1↓ 0=) | +0.000 (0↑ 0↓ 5=) | +253.7 (0↑ 5↓ 0=) | +254.2 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |
| listing | mobile | 5 | +0 (2↑ 2↓ 1=) | +1755 (0↑ 5↓ 0=) | -734 (5↑ 0↓ 0=) | +0.000 (0↑ 0↓ 5=) | +251.1 (0↑ 5↓ 0=) | +254.2 (0↑ 5↓ 0=) | +4 (5↑ 0↓ 0=) |

## Console errors Lighthouse recorded (any run)

- **before**, listing: `network` Failed to load resource: the server responded with a status of 401 (Unauthorized)
- **after:** none

## Per run

| Page | Form factor | Run | Build | Perf | A11y | BP | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| listing | desktop | 1 | before | 90 | 100 | 96 | 358 | 358 | 258 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 1 | after | 97 | 100 | 100 | 332 | 717 | 144 | 0.000 | 559.1 | 155.3 | 254.2 | 87 |
| listing | desktop | 2 | before | 93 | 100 | 96 | 339 | 339 | 213 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 2 | after | 96 | 100 | 100 | 332 | 684 | 161 | 0.000 | 560.3 | 155.3 | 254.2 | 89 |
| listing | desktop | 3 | before | 91 | 100 | 96 | 332 | 332 | 251 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 3 | after | 98 | 100 | 100 | 324 | 1013 | 96 | 0.000 | 560.3 | 155.3 | 254.2 | 89 |
| listing | desktop | 4 | before | 92 | 100 | 96 | 334 | 334 | 236 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 4 | after | 98 | 100 | 100 | 305 | 944 | 92 | 0.000 | 560.3 | 155.3 | 254.2 | 89 |
| listing | desktop | 5 | before | 96 | 100 | 96 | 352 | 352 | 173 | 0.000 | 306.6 | 155.0 | 0.0 | 61 |
| listing | desktop | 5 | after | 95 | 100 | 100 | 327 | 729 | 182 | 0.000 | 560.3 | 155.3 | 254.2 | 89 |
| listing | mobile | 1 | before | 74 | 100 | 96 | 1205 | 1205 | 1499 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 1 | after | 71 | 100 | 100 | 1073 | 3618 | 808 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 2 | before | 72 | 100 | 96 | 950 | 950 | 1942 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 2 | after | 75 | 100 | 100 | 949 | 2299 | 1142 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 3 | before | 72 | 100 | 96 | 948 | 948 | 2032 | 0.000 | 273.3 | 155.0 | 0.0 | 33 |
| listing | mobile | 3 | after | 60 | 100 | 100 | 927 | 4071 | 1711 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
| listing | mobile | 4 | before | 73 | 100 | 96 | 947 | 947 | 1844 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 4 | after | 73 | 100 | 100 | 1145 | 2702 | 1110 | 0.000 | 524.5 | 155.3 | 254.2 | 59 |
| listing | mobile | 5 | before | 72 | 100 | 96 | 1096 | 1096 | 2118 | 0.000 | 273.9 | 155.0 | 0.0 | 34 |
| listing | mobile | 5 | after | 74 | 100 | 100 | 1157 | 2309 | 1236 | 0.000 | 525.1 | 155.3 | 254.2 | 60 |
