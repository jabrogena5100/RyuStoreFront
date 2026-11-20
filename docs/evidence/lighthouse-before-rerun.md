# Lighthouse: before-rerun

> Built from commit `e3ec80e` (the code before the redesign), exported with `git archive` to `~/.cache/storefront-ab/before` and measured back to back with `after-rerun`, so the two are comparable. The first baseline (`lighthouse-before-redesign.md`) was measured about two hours earlier and is not comparable with later runs.

- Lighthouse 13.6.0, HeadlessChrome/159.0.0.0, run 2026-11-20
- Machine: RyuStoreFront, 4 CPUs. Production build (`next build` + `next start`) on localhost against the mock API (`web/e2e/mock-api.mjs`).
- Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.
- Each cell: median (min–max) over the runs listed. Page weight = transferred bytes of all requests (compressed).
- Raw reports: `evidence-raw/lighthouse/before-rerun/` (git-ignored, regenerate with `scripts/lighthouse.sh before-rerun`).

| Page | Form factor | Runs | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 3 | 97 (94–99) | 100 | 96 | 100 | 361 (311–381) | 361 (311–381) | 139 (118–204) | 0.000 | 229.7 (229.7–229.7) | 151.2 | 0.0 | 35 |
| home | mobile | 3 | 74 (74–78) | 100 | 96 | 100 | 1168 (978–1380) | 1168 (978–1380) | 1570 (994–1626) | 0.000 | 224.3 (224.3–224.3) | 151.2 | 0.0 | 30 |
| listing | desktop | 3 | 95 (91–96) | 98 | 96 | 100 | 362 (318–378) | 362 (318–378) | 178 (161–246) | 0.000 | 273.3 (273.3–273.9) | 149.3 | 0.0 | 62 (62–63) |
| listing | mobile | 3 | 72 (72–75) | 98 | 96 | 100 | 941 (938–1075) | 1075 (938–1717) | 1805 (1326–2059) | 0.000 | 241.1 (240.5–241.1) | 149.3 | 0.0 | 33 (32–33) |
| product | desktop | 3 | 94 (93–94) | 100 | 96 | 100 | 330 (319–332) | 330 (319–332) | 194 (193–213) | 0.000 | 232.1 (231.6–232.1) | 150.6 | 0.0 | 39 (38–39) |
| product | mobile | 3 | 73 (72–76) | 100 | 96 | 100 | 895 (830–896) | 895 (830–896) | 1714 (1170–2027) | 0.000 | 221.6 (221.6–222.2) | 150.6 | 0.0 | 29 (29–30) |
| sign-in | desktop | 3 | 97 (95–98) | 100 | 96 | 100 | 334 (300–340) | 334 (300–340) | 154 (138–185) | 0.000 | 223.4 (223.4–223.9) | 151.1 | 0.0 | 36 (36–37) |
| sign-in | mobile | 3 | 79 (77–84) | 100 | 96 | 100 | 941 (941–1003) | 941 (941–1003) | 901 (663–1092) | 0.000 | 216.9 (216.4–216.9) | 151.1 | 0.0 | 30 (29–30) |

## Per run

| Page | Form factor | Run | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 1 | 99 | 100 | 96 | 100 | 311 | 311 | 118 | 0.000 | 229.7 | 151.2 | 0.0 | 35 |
| home | desktop | 2 | 94 | 100 | 96 | 100 | 381 | 381 | 204 | 0.000 | 229.7 | 151.2 | 0.0 | 35 |
| home | desktop | 3 | 97 | 100 | 96 | 100 | 361 | 361 | 139 | 0.000 | 229.7 | 151.2 | 0.0 | 35 |
| home | mobile | 1 | 78 | 100 | 96 | 100 | 978 | 978 | 994 | 0.000 | 224.3 | 151.2 | 0.0 | 30 |
| home | mobile | 2 | 74 | 100 | 96 | 100 | 1168 | 1168 | 1626 | 0.000 | 224.3 | 151.2 | 0.0 | 30 |
| home | mobile | 3 | 74 | 100 | 96 | 100 | 1380 | 1380 | 1570 | 0.000 | 224.3 | 151.2 | 0.0 | 30 |
| listing | desktop | 1 | 95 | 98 | 96 | 100 | 362 | 362 | 178 | 0.000 | 273.3 | 149.3 | 0.0 | 62 |
| listing | desktop | 2 | 96 | 98 | 96 | 100 | 318 | 318 | 161 | 0.000 | 273.3 | 149.3 | 0.0 | 62 |
| listing | desktop | 3 | 91 | 98 | 96 | 100 | 378 | 378 | 246 | 0.000 | 273.9 | 149.3 | 0.0 | 63 |
| listing | mobile | 1 | 75 | 98 | 96 | 100 | 1075 | 1075 | 1326 | 0.000 | 241.1 | 149.3 | 0.0 | 33 |
| listing | mobile | 2 | 72 | 98 | 96 | 100 | 941 | 1717 | 1805 | 0.000 | 241.1 | 149.3 | 0.0 | 33 |
| listing | mobile | 3 | 72 | 98 | 96 | 100 | 938 | 938 | 2059 | 0.000 | 240.5 | 149.3 | 0.0 | 32 |
| product | desktop | 1 | 93 | 100 | 96 | 100 | 319 | 319 | 213 | 0.000 | 231.6 | 150.6 | 0.0 | 38 |
| product | desktop | 2 | 94 | 100 | 96 | 100 | 330 | 330 | 194 | 0.000 | 232.1 | 150.6 | 0.0 | 39 |
| product | desktop | 3 | 94 | 100 | 96 | 100 | 332 | 332 | 193 | 0.000 | 232.1 | 150.6 | 0.0 | 39 |
| product | mobile | 1 | 72 | 100 | 96 | 100 | 895 | 895 | 2027 | 0.000 | 222.2 | 150.6 | 0.0 | 30 |
| product | mobile | 2 | 73 | 100 | 96 | 100 | 896 | 896 | 1714 | 0.000 | 221.6 | 150.6 | 0.0 | 29 |
| product | mobile | 3 | 76 | 100 | 96 | 100 | 830 | 830 | 1170 | 0.000 | 221.6 | 150.6 | 0.0 | 29 |
| sign-in | desktop | 1 | 95 | 100 | 96 | 100 | 340 | 340 | 185 | 0.000 | 223.4 | 151.1 | 0.0 | 36 |
| sign-in | desktop | 2 | 98 | 100 | 96 | 100 | 300 | 300 | 138 | 0.000 | 223.9 | 151.1 | 0.0 | 37 |
| sign-in | desktop | 3 | 97 | 100 | 96 | 100 | 334 | 334 | 154 | 0.000 | 223.4 | 151.1 | 0.0 | 36 |
| sign-in | mobile | 1 | 77 | 100 | 96 | 100 | 941 | 941 | 1092 | 0.000 | 216.4 | 151.1 | 0.0 | 29 |
| sign-in | mobile | 2 | 79 | 100 | 96 | 100 | 1003 | 1003 | 901 | 0.000 | 216.9 | 151.1 | 0.0 | 30 |
| sign-in | mobile | 3 | 84 | 100 | 96 | 100 | 941 | 941 | 663 | 0.000 | 216.9 | 151.1 | 0.0 | 30 |
