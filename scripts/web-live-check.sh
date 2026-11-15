#!/usr/bin/env bash
# Run the frontend checks against the REAL API. The backend must already be running with demo data (see the header of
# web/live/catalogue.spec.ts). Everything else runs in Docker.
#
#   scripts/web-live-check.sh                      # API on http://localhost:8080
#   API_ORIGIN=http://localhost:9090 scripts/web-live-check.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO="$PWD"
UIDGID="$(id -u):$(id -g)"
PW_IMAGE="${PW_IMAGE:-mcr.microsoft.com/playwright:v1.65.1-noble}"
export API_ORIGIN="${API_ORIGIN:-http://localhost:8080}"

curl -sf -o /dev/null "$API_ORIGIN/api/categories" || { echo "The API is not answering at $API_ORIGIN (start it with the seed profile first)."; exit 1; }

cleanup() { docker rm -f storefront-web-live >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

# Start from a clean build: Next keeps fetched API responses in web/.next/cache, and a stale entry from an earlier run
# (for example the mock API) would be served once after its 30 s expiry.
rm -rf web/.next
# API_ORIGIN is read at build time (the /api rewrite) and at run time (server components).
docker run --rm --network host --user "$UIDGID" -e HOME=/tmp/home -e npm_config_cache=/npm-cache -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
  -v "$REPO":/workspace -v "$HOME/.cache/storefront-npm":/npm-cache -w /workspace/web node:26-bookworm-slim npm run build
docker run -d --name storefront-web-live --network host --user "$UIDGID" -e HOME=/tmp/home -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
  -v "$REPO":/workspace -w /workspace/web node:26-bookworm-slim npm run start >/dev/null
for _ in $(seq 1 30); do curl -sf -o /dev/null http://localhost:3000/ && break; sleep 1; done

docker run --rm --network host --ipc=host --user "$UIDGID" -e HOME=/tmp/home \
  -v "$REPO":/workspace -w /workspace/web "$PW_IMAGE" npx playwright test -c playwright.live.config.ts
