#!/usr/bin/env bash
# Lighthouse (performance, accessibility, best practices, SEO) on the main pages, mobile and desktop, 3 runs each,
# against a production build of web/ served by `next start` and the mock API (same setup as scripts/design-shots.sh).
# Everything runs in Docker (pinned Playwright image for Chromium, Lighthouse pinned below).
#
#   scripts/lighthouse.sh <label>          # e.g. before-redesign, after-redesign
#   RUNS=5 PAGES="home listing" scripts/lighthouse.sh <label>
#
# Output: evidence-raw/lighthouse/<label>/*.json (full reports, git-ignored because of size) and
#         docs/evidence/lighthouse-<label>.md (per-run values plus median and range, committed).
# Numbers are LOCAL: one VM, localhost (no real network), Lighthouse's default simulated throttling.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO="$PWD"
LABEL="${1:?usage: scripts/lighthouse.sh <label>}"
RUNS="${RUNS:-3}"
LH_VERSION="13.6.0"
PW_IMAGE="${PW_IMAGE:-mcr.microsoft.com/playwright:v1.65.1-noble}"
CHROME=/ms-playwright/chromium-1255/chrome-linux64/chrome
UIDGID="$(id -u):$(id -g)"
CACHE="${NPM_CACHE_DIR:-$HOME/.cache/storefront-npm}"

declare -A PATHS=(
  [home]="/"
  [listing]="/products"
  [product]="/products/aurel-court-sneaker"
  [sign-in]="/sign-in"
)
PAGES="${PAGES:-home listing product sign-in}"

for c in storefront-web storefront-web-live; do
  if [ -n "$(docker ps -q -f "name=^${c}$")" ]; then echo "Another web pipeline is running ($c). Wait for it, then run this again."; exit 1; fi
done
if curl -sf -o /dev/null http://localhost:3000/; then echo "Something is already serving http://localhost:3000. Stop it first."; exit 1; fi

cleanup() { docker rm -f storefront-web storefront-mock-api >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

MOCK_PORT="${MOCK_API_PORT:-8089}"
export API_ORIGIN="http://localhost:$MOCK_PORT"
if curl -sf -o /dev/null "$API_ORIGIN/api/categories"; then echo "Port $MOCK_PORT is already in use; set MOCK_API_PORT."; exit 1; fi
docker run -d --name storefront-mock-api --network host --user "$UIDGID" -e HOME=/tmp/home -e MOCK_API_PORT="$MOCK_PORT" \
  -v "$REPO":/workspace -w /workspace/web node:26-bookworm-slim node e2e/mock-api.mjs >/dev/null
for _ in $(seq 1 30); do curl -sf -o /dev/null "$API_ORIGIN/api/categories" && break; sleep 1; done
curl -sf "$API_ORIGIN/api/products?pageSize=1" | grep -q '"total":' || { echo "The mock API is not answering on $MOCK_PORT."; exit 1; }

rm -rf web/.next
scripts/web.sh npm run build
docker run -d --name storefront-web --network host --user "$UIDGID" -e HOME=/tmp/home -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
  -v "$REPO":/workspace -w /workspace/web node:26-bookworm-slim npm run start >/dev/null
for _ in $(seq 1 30); do curl -sf -o /dev/null http://localhost:3000/ && break; sleep 1; done
# Warm each page once so the first measured run does not pay for Next's first render of the route.
for page in $PAGES; do curl -sf -o /dev/null "http://localhost:3000${PATHS[$page]}"; done

OUT="evidence-raw/lighthouse/$LABEL"
rm -rf "$OUT"; mkdir -p "$OUT" "$CACHE"
for page in $PAGES; do
  for ff in mobile desktop; do
    preset=""; [ "$ff" = desktop ] && preset="--preset=desktop"
    for i in $(seq 1 "$RUNS"); do
      echo "lighthouse $page $ff run $i"
      docker run --rm --network host --ipc=host --user "$UIDGID" -e HOME=/tmp/home -e npm_config_cache=/npm-cache \
        -e CHROME_PATH="$CHROME" -v "$REPO":/workspace -v "$CACHE":/npm-cache -w /workspace "$PW_IMAGE" \
        npx -y "lighthouse@$LH_VERSION" "http://localhost:3000${PATHS[$page]}" $preset --quiet \
          --chrome-flags="--headless=new --no-sandbox" \
          --only-categories=performance,accessibility,best-practices,seo \
          --output=json --output-path="$OUT/$page-$ff-$i.json" >/dev/null
    done
  done
done

python3 -I scripts/lighthouse-summary.py "$OUT" "$LABEL" "$(uname -n), $(nproc) CPUs" > "docs/evidence/lighthouse-$LABEL.md"
cat "docs/evidence/lighthouse-$LABEL.md"
