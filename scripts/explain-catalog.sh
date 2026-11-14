#!/usr/bin/env bash
# Print EXPLAIN (ANALYZE, BUFFERS) plans for the main catalog queries against the Compose Postgres.
# Seed the database first (see docs/explain/README.md). Read-only apart from VACUUM ANALYZE.
#
#   scripts/explain-catalog.sh                                  current query shape
#   scripts/explain-catalog.sh docs/explain/catalog-explain-before.sql   original shape (before V7)
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
SQL_FILE="${1:-docs/explain/catalog-explain.sql}"
docker compose exec -T postgres sh -c 'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$SQL_FILE"
