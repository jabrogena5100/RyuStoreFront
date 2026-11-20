# Lighthouse: exp-no-palette-drawer

- Lighthouse 13.6.0, HeadlessChrome/159.0.0.0, run 2026-11-20
- Machine: RyuStoreFront, 4 CPUs. Production build (`next build` + `next start`) on localhost against the mock API (`web/e2e/mock-api.mjs`).
- Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.
- Each cell: median (min–max) over the runs listed. Page weight = transferred bytes of all requests (compressed).
- Raw reports: `evidence-raw/lighthouse/exp-no-palette-drawer/` (git-ignored, regenerate with `scripts/lighthouse.sh exp-no-palette-drawer`).

| Page | Form factor | Runs | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sign-in | desktop | 3 | 87 (84–98) | 100 | 96 | 100 | 354 (289–372) | 354 (289–372) | 314 (121–361) | 0.000 | 249.7 (249.1–249.7) | 151.6 | 0.0 | 36 (35–36) |
| sign-in | mobile | 3 | 75 (73–78) | 100 | 96 | 100 | 956 (933–1064) | 956 (933–1064) | 1380 (1067–1803) | 0.000 | 242.5 (242.5–242.6) | 151.6 | 0.0 | 30 |

## Per run

| Page | Form factor | Run | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| sign-in | desktop | 1 | 87 | 100 | 96 | 100 | 372 | 372 | 314 | 0.000 | 249.7 | 151.6 | 0.0 | 36 |
| sign-in | desktop | 2 | 84 | 100 | 96 | 100 | 354 | 354 | 361 | 0.000 | 249.7 | 151.6 | 0.0 | 36 |
| sign-in | desktop | 3 | 98 | 100 | 96 | 100 | 289 | 289 | 121 | 0.000 | 249.1 | 151.6 | 0.0 | 35 |
| sign-in | mobile | 1 | 75 | 100 | 96 | 100 | 1064 | 1064 | 1380 | 0.000 | 242.6 | 151.6 | 0.0 | 30 |
| sign-in | mobile | 2 | 73 | 100 | 96 | 100 | 956 | 956 | 1803 | 0.000 | 242.5 | 151.6 | 0.0 | 30 |
| sign-in | mobile | 3 | 78 | 100 | 96 | 100 | 933 | 933 | 1067 | 0.000 | 242.5 | 151.6 | 0.0 | 30 |
