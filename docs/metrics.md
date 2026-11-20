# Metrics ledger

Every number this project quotes (README, resume, portfolio) has a row here. **A number with no row does not exist.**

Status labels:
- **Run**: Claude Code ran the command and read the output.
- **User-reported**: the owner ran it and pasted the output (or Claude Code read the raw files the run left behind; the row says which).
- **Documented**: derived from code or existing docs, not from a run.
- **Not run**: planned, no value yet.

Rules: timings are the median and range of at least 3 runs. Rows from before 2026-11-20 that were single runs say so and are marked
for re-measurement. Before/after pairs measure the "before" first. Nothing is rounded up, extrapolated or described as
"scalable", "production-ready" or "high-traffic". Local numbers are labelled local.

Older detail behind each row is in `docs/verification.md` (the dated log of claims). This file is the index of numbers.

## Backend correctness

| ID | Metric | Value | Setup | Date | Status | Command / raw output |
| --- | --- | --- | --- | --- | --- | --- |
| B1 | Backend tests, whole suite | **241 tests, 0 failures, 0 errors, 0 skipped** (27 classes) | Dev VM, Java 25, Postgres 18.7 and Mailpit in Testcontainers | 2026-11-15 (reports 10:28 UTC) | User-reported (raw Surefire reports read by Claude Code on 2026-11-20) | `./mvnw -B verify`; per-class counts in `docs/evidence/2026-11-15-backend-tests-surefire.txt`. Supersedes B1a |
| B1a | Backend tests, earlier run | 235 tests, 0 failures, 4 min 14 s | Same | 2026-11-15 08:35 UTC | User-reported | `./mvnw verify`, pasted log tail (verification.md row 71). **Superseded by B1**: quote 241, not 235 |
| B2 | Duplicate Stripe webhooks | Same event 3 times in sequence and **6 at the same moment** → 1 paid order, 1 `payments` row, 1 `order.paid` event, 1 e-mail record | One JVM, Testcontainers Postgres 18.7, signatures from an independent HMAC-SHA256 | 2026-11-14 | Run | `./mvnw -B verify`, `StripeWebhookTests` (19 tests, now in B1's file). Out-of-order cases in the same class: expired→completed is refunded once, completed→expired stays paid |
| B3 | Overselling under concurrency (reservation) | **50 threads, 10 units → exactly 10 reservations and 0 stock, in each of 20 consecutive rounds** | One JVM, threads released by a latch, Hikari pool 60, Postgres 18.7 in Testcontainers, one VM. Not a traffic claim | 2026-11-14 | User-reported | `./mvnw -B test -Dtest='StockReservation*'`; constants `BUYERS=50, UNITS=10, ROUNDS=20` in `StockReservationConcurrencyTests` |
| B4 | Overselling under concurrency (checkout) | 20 buyers, 5 units → exactly 5 orders, in each of 3 rounds | One JVM, service called directly, Testcontainers Postgres | 2026-11-14 | User-reported | `CheckoutConcurrencyTests` |
| B5 | Worker killed mid-job | SIGKILL on a separate worker JVM: the job's writes vanish, the event stays pending with 0 attempts, a restarted worker completes it **exactly once** | Two child JVMs against the test's Postgres; test 51.8 s in B1's run | 2026-11-14 | User-reported | `WorkerKillTests` (1 test) |
| B6 | Order state machine coverage | **All 36 status pairs** (6 statuses × 6) tested; 10 concurrent identical transitions apply once; payment racing cancellation has one winner in each of 20 rounds | Unit table plus real service and Postgres | 2026-11-14 | User-reported (the 36 = 6 × 6 count is Documented from `OrderStatus.java:22`) | `OrderStatusTests` 8, `OrderTransitionTests` 8 |
| B7 | Outbox exactly-once under competing workers | 4 concurrent workers handle 60 events, each exactly once | One JVM, handler sleeps 5 ms. Not a throughput claim | 2026-11-14 | User-reported | `OutboxProcessorTests` |
| B8 | Concurrency proof at larger scale (500 buyers, 100 units, N rounds) | — | — | — | Not run | Planned; same test with larger constants, run 3 times |

## Database and catalogue

| ID | Metric | Value | Setup | Date | Status | Command / raw output |
| --- | --- | --- | --- | --- | --- | --- |
| D1 | Listing count query, before/after `min_price_minor` (V7) | **518 ms → 22 ms** | 50,000 products / 750,680 variants, local Postgres 18.7 in Docker, warm cache, **single runs** (re-measure with 3 runs) | 2026-11-14 | User-reported | `scripts/explain-catalog.sh`; `docs/explain/plans-50000-before-warm.txt`, `plans-50000-after.txt`, table in `docs/explain/README.md` (Q6) |
| D2 | Category listing sorted by price, before/after | **227 ms → 20 ms** | Same | 2026-11-14 | User-reported | Same files (Q3) |
| D3 | Deep page (offset 960), before/after | 28 ms → 5.1 ms | Same | 2026-11-14 | User-reported | Same files (Q5) |
| D4 | Full-text search ranking choice | `ts_rank_cd` 410 ms vs `ts_rank` 22 ms on the page query (~1,460 matches) | Same, single runs | 2026-11-14 | User-reported | `docs/explain/plans-50000-search-experiments.txt` |
| D5 | Seed 50,000 products | 196.5 s (`Seeded 50000 products in 196530 ms`) | Local Postgres in Docker, batched inserts, single run, before the V7 trigger | 2026-11-14 | User-reported | `seed` profile log line |
| D6 | Catalogue seeded for the demo (products, variants, images, storage) | — | — | — | Not run | Phase 4 |

## Frontend

| ID | Metric | Value | Setup | Date | Status | Command / raw output |
| --- | --- | --- | --- | --- | --- | --- |
| F1 | axe scan (WCAG 2.0/2.1 A and AA rules) | **119 pages, 0 violations** | Chromium only, 1440 and 390 px, mock API, Playwright 1.65.1, `@axe-core/playwright` 4.14.0. axe covers only the automatable share of WCAG | 2026-11-15 | Run | `scripts/design-shots.sh`; `design/screens/AXE.txt` |
| F2 | Frontend test counts | `web/live`: 17 tests (catalogue 6, auth and cart 6, checkout 4, screenshots 1); `web/e2e`: 1 screenshot-and-axe test over 119 pages | Counted from source | 2026-11-20 | Documented | `grep -c 'test(' web/live/*.spec.ts web/e2e/*.spec.ts`. Last full pass with output seen: `14 passed (51.6s)` before the checkout specs existed (verification.md row 77) |
| F3 | Lighthouse mobile and desktop per main page (score, LCP, CLS, TBT), before and after redesign | — | — | — | Not run | Phase 5 (the "before" is measured first, on the current design) |
| F4 | Page weight (JS KB and total KB), before and after | — | — | — | Not run | Phase 5 |
| F5 | Image bytes original vs served, % saved | — | — | — | Not run | Phase 4/5 |
| F6 | axe at 320/390/820/1440 px after redesign; horizontal overflow at 320 px | — | — | — | Not run | Phase 3 |

## API performance (local)

| ID | Metric | Value | Setup | Date | Status | Command / raw output |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | Single-request timings of the listing's queries against the real API | 20 to 90 ms per query | Owner's VM, 49,507 products, `curl`, **single requests** (not a latency distribution) | 2026-11-15 | Run | verification.md row 72 (output not saved to a file) |
| P2 | p50/p95 latency and throughput: catalogue, product, checkout | — | — | — | Not run | k6, local, phase 5 |

## Cloud (AWS, us-west-2)

| ID | Metric | Value | Setup | Date | Status | Command / raw output |
| --- | --- | --- | --- | --- | --- | --- |
| C1 | Real checkout through the cloud API | **1 order, paid by Stripe's webhook** (`checkout.session.completed` delivered, HTTP 200) | API on Fargate 0.5 vCPU / 1 GB behind ALB over HTTPS, RDS Postgres 18.6, Stripe test mode, card 4242, no worker | 2026-11-15 | User-reported | `BASE=https://api.<domain> bash scripts/manual-checkout.sh <sku>`; Stripe delivery log; verification.md row 56 |
| C2 | Session-stage resources | **31** (18 network and database + 13 application); destroy plan `31 to destroy`, applied; `state list` empty afterwards | Terraform 1.17.2, AWS provider 6.73.0 | 2026-11-15 | User-reported | Plan outputs; the `Destroy complete!` line itself was **not captured**, so say "a 31-resource destroy plan, state empty after", not "31 destroyed" |
| C3 | Post-destroy audit | **13 direct checks, all `ok`**; only foundation resources remain | `scripts/aws-audit.sh` | 2026-11-15 | User-reported | verification.md row 57 |
| C4 | API cold start on Fargate | **55.2 s** (`Started StorefrontApplication in 55.199 seconds`) | 0.5 vCPU / 1 GB, x86_64, first start on a fresh RDS: includes 10 Flyway migrations (1.4 s) and seeding 100 products (1.4 s). **Single run** | 2026-11-15 | User-reported | CloudWatch log `/storefront/api` |
| C4a | Same image, dev VM | 22.3 s | **Worker** profile, no CPU limit, schema already migrated, single run. **Not comparable with C4** (different profile, no migration or seed) | 2026-11-15 | User-reported | `docker run` log |
| C5 | Provisioning times | RDS 5 min 21 s; ALB 2 min 25 s; certificate validation record 31–32 s | Single applies (two separate slices) | 2026-11-15, 2026-11-19 | User-reported | Terraform apply output |
| C6 | Image size in ECR | 196,588,254 bytes compressed | Tag `a969c9cb18bc` | 2026-11-15 | User-reported | `aws ecr describe-images` |
| C7 | Session with API + worker + frontend: time from apply to first checkout | — | — | — | Not run | Phase 6 |
| C8 | Webhook replay against the cloud endpoint (events sent, duplicates, paid orders) | — | — | — | Not run | Phase 6 |
| C9 | Real cost per session and per month of the foundation (Cost Explorer) | — | — | — | Not run | Phase 6; list-price estimates in `infra/README.md` are not measurements |
| C10 | Time to rebuild from zero using `infra/README.md` | — | — | — | Not run | Phase 6 |
