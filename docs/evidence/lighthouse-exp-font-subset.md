# Lighthouse: exp-font-subset

- Lighthouse 13.6.0, HeadlessChrome/159.0.0.0, run 2026-11-20
- Machine: RyuStoreFront, 4 CPUs. Production build (`next build` + `next start`) on localhost against the mock API (`web/e2e/mock-api.mjs`).
- Throttling: Lighthouse defaults (mobile: simulated slow 4G and 4x CPU slowdown; desktop: `--preset=desktop`). Local, not field data.
- Each cell: median (min–max) over the runs listed. Page weight = transferred bytes of all requests (compressed).
- Raw reports: `evidence-raw/lighthouse/exp-font-subset/` (git-ignored, regenerate with `scripts/lighthouse.sh exp-font-subset`).

| Page | Form factor | Runs | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 3 | 96 (95–99) | 100 | 96 | 100 | 353 (344–386) | 353 (344–386) | 171 (110–191) | 0.000 | 273.4 (272.8–274.0) | 156.1 | 0.0 | 45 (44–46) |
| home | mobile | 3 | 72 (71–75) | 100 | 96 | 100 | 983 (946–1562) | 983 (946–1562) | 1653 (1300–2296) | 0.000 | 260.6 (260.6–260.6) | 156.1 | 0.0 | 34 |

## Per run

| Page | Form factor | Run | Perf | A11y | BP | SEO | FCP ms | LCP ms | TBT ms | CLS | Total KB | JS KB | Img KB | Requests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| home | desktop | 1 | 99 | 100 | 96 | 100 | 344 | 344 | 110 | 0.000 | 273.4 | 156.1 | 0.0 | 45 |
| home | desktop | 2 | 96 | 100 | 96 | 100 | 386 | 386 | 171 | 0.000 | 272.8 | 156.1 | 0.0 | 44 |
| home | desktop | 3 | 95 | 100 | 96 | 100 | 353 | 353 | 191 | 0.000 | 274.0 | 156.1 | 0.0 | 46 |
| home | mobile | 1 | 75 | 100 | 96 | 100 | 983 | 983 | 1300 | 0.000 | 260.6 | 156.1 | 0.0 | 34 |
| home | mobile | 2 | 71 | 100 | 96 | 100 | 946 | 946 | 2296 | 0.000 | 260.6 | 156.1 | 0.0 | 34 |
| home | mobile | 3 | 72 | 100 | 96 | 100 | 1562 | 1562 | 1653 | 0.000 | 260.6 | 156.1 | 0.0 | 34 |
