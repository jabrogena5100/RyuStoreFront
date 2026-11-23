#!/usr/bin/env bash
# Run the live frontend checks (web/live/) against the DEPLOYED shop, not a local build: real DNS, TLS, ALB host routing,
# the Next.js container, the API task, RDS and product photos from CloudFront. Nothing is built or started here.
#
#   scripts/web-cloud-check.sh                              # https://ryustorefront.com
#   BASE_URL=https://ryustorefront.com scripts/web-cloud-check.sh
#
# The tests create customers (live-...@example.com), carts and pending orders in the cloud database; the session's
# destroy removes them. Screenshots go to docs/evidence/cloud-shots/ (desktop and phone; every photo must decode).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO="$PWD"
PW_IMAGE="${PW_IMAGE:-mcr.microsoft.com/playwright:v1.65.1-noble}"
BASE_URL="${BASE_URL:-https://ryustorefront.com}"

code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/healthz" || true)"
[ "$code" = 200 ] || { echo "$BASE_URL/healthz answered '$code', not 200. Is the web service running and the apex record in place?"; exit 1; }
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/categories" || true)"
[ "$code" = 200 ] || { echo "$BASE_URL/api/categories answered '$code', not 200 (the frontend's proxy to the API)."; exit 1; }

docker run --rm --network host --ipc=host --user "$(id -u):$(id -g)" -e HOME=/tmp/home -e "BASE_URL=$BASE_URL" \
  -e LIVE_SHOTS_DIR=/workspace/docs/evidence/cloud-shots \
  -v "$REPO":/workspace -w /workspace/web "$PW_IMAGE" npx playwright test -c playwright.live.config.ts
