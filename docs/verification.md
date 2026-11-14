# Verification log

Each claim is marked **run** (output seen), **user-reported** (the owner ran it and reported the result) or **not done**. Nothing here is a claim about performance or scale unless a row has numbers and a setup.

| Date | Phase | Claim | Status | Evidence |
| --- | --- | --- | --- | --- |
| 2026-11-14 | 0 | `./mvnw -B verify` passes locally (context loads against Postgres in Testcontainers, jar builds) | user-reported | Maven output: 1 test, 0 failures, BUILD SUCCESS, 51 s |
| 2026-11-14 | 0 | CI workflow `ci` is green on `main` | user-reported | `gh run list`: success on the Phase 0 push |
| 2026-11-14 | 0 | Ruleset `main-protection` active; secret scanning and push protection enabled | user-reported | `gh api` output: enforcement `active`, both `enabled` |
| 2026-11-14 | 0 | `scripts/aws-terraform.sh version` runs Terraform 1.17.2 in Docker using `aws login` credentials | user-reported | Output: `Terraform v1.17.2`; `get-caller-identity` returned the IAM user |
| 2026-11-14 | 1 | Migrations V1-V4 apply on a fresh Postgres 18.7 (Testcontainers); constraint tests pass; `./mvnw -B verify` green locally and in CI | user-reported | PR #1 check `ci/build` green, squash-merged as `5cab7c4`; Dependabot PR #2 (Modulith 2.1.1 to 2.1.2) also green and merged |
| 2026-11-14 | 2 | `EXPLAIN` shows the intended indexes for the main catalog queries at 1,000 and 50,000 products; before/after for the `min_price_minor` change recorded | user-reported | `docs/explain/plans-*.txt` and the results table in `docs/explain/README.md`. 50,000 products / 750,680 variants, warm cache, single runs: boots-by-price 227 ms to 20 ms, count 518 ms to 22 ms, deep page 28 ms to 5 ms. Text search ranking: `ts_rank_cd` 410 ms vs `ts_rank` 22 ms on the page query (experiment) |
| 2026-11-14 | 2 | Pagination returns every active product exactly once; filters and sorts verified against independent SQL counts; hostile search string is bound, not concatenated; anonymous writes rejected; 400/404 not masked as 403 | user-reported | `./mvnw -B verify`: 71 tests, 0 failures, BUILD SUCCESS (includes `CatalogApiTests`, `MinPriceTriggerTests`, `SchemaConstraintTests`) |
| 2026-11-14 | 2 | Seeding 50,000 products takes about 197 s with batched inserts | user-reported | Log line `Seeded 50000 products in 196530 ms` (local Postgres in Docker; this was the first, pre-trigger run) |
| 2026-11-14 | 3a | No overselling under concurrency: 50 concurrent threads reserving 1 unit each against 10 in stock yield exactly 10 reservations and 0 stock left, in each of 20 consecutive rounds (single test run) | user-reported | `./mvnw -B test -Dtest='StockReservation*'`: 15 tests, 0 failures, BUILD SUCCESS. Setup: one JVM, 50 threads released together by a latch, Hikari pool 60, Postgres 18.7 in Testcontainers on one VM. Not a production-traffic claim. |
| 2026-11-14 | 3a | Opposite-order two-variant reservations (40 buyers) cause no deadlock; 8 concurrent expiry sweeps release each of 100 reservations exactly once and restore stock exactly once | user-reported | Same run: `StockReservationConcurrencyTests` passed |
| 2026-11-14 | 3a | Full suite passes with slice 3a | user-reported | `./mvnw -B verify`: BUILD SUCCESS (test total line not captured) |
