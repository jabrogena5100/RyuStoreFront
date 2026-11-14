# Catalog query plans

Purpose: show which indexes the main catalog queries use, and keep the output as evidence.
Plans depend on table size. At 1,000 products Postgres will often choose a sequential scan, and
that is correct, so run at 1,000 and at 50,000 and record both.

## Seed and explain

```
set -a; source .env; set +a
docker compose up -d
STOREFRONT_SEED_PRODUCTS=1000 ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
# wait for "Seeded 1000 products in ... ms", then Ctrl+C
scripts/explain-catalog.sh | tee docs/explain/plans-1000.txt
```

For 50,000 products, empty the tables first (this deletes catalogue data in the local dev
database only), then seed again:

```
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "TRUNCATE products, categories, variants RESTART IDENTITY CASCADE"'
STOREFRONT_SEED_PRODUCTS=50000 ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
scripts/explain-catalog.sh | tee docs/explain/plans-50000.txt
```

## Results

Setup: local Docker Postgres 18.7 on the development VM, default settings, `VACUUM (ANALYZE)` before
each run, deterministic seed (`DemoDataSeeder`, seed 42). 50,000 products / 750,680 variants. Times are
`Execution Time` from `EXPLAIN (ANALYZE, BUFFERS)` on a warm cache, one run each (not a benchmark: single
run, no load, no percentiles). Raw output: `plans-1000.txt`, `plans-50000-before.txt` (cold cache, original
queries), `plans-50000-before-warm.txt`, `plans-50000-after.txt`.

"Old shape" is `catalog-explain-before.sql` (price aggregated per matching product). "New shape" is
`catalog-explain.sql` after migration V7 (trigger-maintained `products.min_price_minor`, page first, then
join) and is what `ProductSearchRepository` runs.

| Query | 1,000 products, old | 50,000, old (warm) | 50,000, new (warm) | Index on the new plan |
| --- | --- | --- | --- | --- |
| Q1 newest, page 1 | 0.9 ms | 1.2 ms | 1.8 ms | `products_active_newest_idx`, `variants_product_cover_idx` (index-only) |
| Q2 text search "chelsea boot" | 6.3 ms (seq scan) | 425 ms | 467 ms | `products_search_idx` (GIN bitmap) |
| Q3 category boots, price asc | 11.5 ms (seq scan) | 227 ms | 20 ms | `products_price_idx` |
| Q4 women, size 8 in stock, newest | 2.6 ms | 5.6 ms | 5.2 ms | `products_active_newest_idx`, `variants_size_idx` |
| Q5 deep page, offset 960 | 21.6 ms | 28 ms | 5.1 ms | `products_active_newest_idx` |
| Q6 count beside every listing | 23.4 ms | 518 ms | 22 ms | `products_price_idx` (index-only) |
| Q7 price range 200-300, price asc | not measured | not measured | 1.1 ms | `products_price_idx` |

What changed and why:
- At 1,000 products Postgres chose sequential scans for Q2, Q3 and Q6. That is correct for a 58-page table;
  the indexes only matter from tens of thousands of rows.
- Q3, Q5 and Q6 were slow because the price came from an aggregate over every variant of every matching
  product, so price sort, price filter and count could not use an index. V7 stores the cheapest active
  price on the product (maintained by triggers that stock updates do not fire) and the page query now
  looks up currency and stock for only the rows it returns.
- Q1 and Q4 were already fast and did not change.
- **Q2 did not improve from the V7 change** (425 to 467 ms, within noise): the price aggregate was not its
  bottleneck. `catalog-explain-search-experiments.sql` isolated the cost, measured on the page query alone at
  50,000 products with about 1,460 matches: `ts_rank_cd` 410 ms, `ts_rank` 22 ms, no ranking 11.6 ms; a second
  query with about 1,720 matches took 737 ms with `ts_rank_cd`. The GIN index finds the matches in about 1 ms;
  `ts_rank_cd` computes term proximity for every match. The repository now uses `ts_rank`, which still honours
  the name/brand/description weights but not term proximity. The Q2 row above is the `ts_rank_cd` measurement;
  the full Q2 statement has not been re-run with `ts_rank`, only the page query in the experiment.
- Known limit: offset pagination reads and discards all earlier rows. The UI is not expected to page deeply;
  keyset pagination is the next step if it did.
