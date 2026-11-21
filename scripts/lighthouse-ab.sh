#!/usr/bin/env bash
# Lighthouse A/B: an older commit ("before") and the working tree ("after"), both production builds, both against the
# SAME running API, measured interleaved in one session. Simulated throttling on this VM drifts between sessions
# (docs/metrics.md F3 vs F3c), so only runs made side by side like this are compared.
#
#   scripts/lighthouse-ab.sh <label> <before-commit>
#   RUNS=5 PAGES="home product" PRODUCT=<slug> API_ORIGIN=http://localhost:8080 scripts/lighthouse-ab.sh photos 0e1f1ef
#
# Needs: the API running with demo data at API_ORIGIN (default http://localhost:8080; the real API, not the mock),
# web/node_modules installed (scripts/web.sh npm ci). The before commit must use the same web/package-lock.json
# (its node_modules are copied from web/).
# Order alternates per run (odd runs: before then after; even runs: after then before) so neither build always goes first.
# Output: evidence-raw/lighthouse/<label>-{before,after}/*.json (git-ignored) and docs/evidence/lighthouse-<label>.md.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO="$PWD"
LABEL="${1:?usage: scripts/lighthouse-ab.sh <label> <before-commit>}"
BEFORE_REF="${2:?usage: scripts/lighthouse-ab.sh <label> <before-commit>}"
RUNS="${RUNS:-5}"
LH_VERSION="13.6.0"
PW_IMAGE="${PW_IMAGE:-mcr.microsoft.com/playwright:v1.65.1-noble}"
NODE_IMAGE="${NODE_IMAGE:-node:26-bookworm-slim}"
CHROME=/ms-playwright/chromium-1255/chrome-linux64/chrome
UIDGID="$(id -u):$(id -g)"
CACHE="${NPM_CACHE_DIR:-$HOME/.cache/storefront-npm}"
export API_ORIGIN="${API_ORIGIN:-http://localhost:8080}"
PRODUCT="${PRODUCT:-ostrava-atelier-umber-chelsea-boot}"

declare -A PATHS=(
  [home]="/"
  [listing]="/products"
  [product]="/products/$PRODUCT"
  [sign-in]="/sign-in"
)
PAGES="${PAGES:-home listing product sign-in}"
declare -A PORT=([before]=3001 [after]=3000)

BEFORE_SHA="$(git rev-parse --short "$BEFORE_REF^{commit}")"
AFTER_DESC="$(git rev-parse --short HEAD)$( [ -n "$(git status --porcelain -- web)" ] && echo ' + uncommitted changes in web/')"
git diff --quiet "$BEFORE_SHA" -- web/package-lock.json || { echo "web/package-lock.json differs between $BEFORE_SHA and the working tree; this script reuses one node_modules."; exit 1; }

curl -sf -o /dev/null "$API_ORIGIN/api/categories" || { echo "The API is not answering at $API_ORIGIN (start it with the seed profile first)."; exit 1; }
curl -sf -o /dev/null "$API_ORIGIN/api/products/$PRODUCT" || { echo "Product $PRODUCT not found at $API_ORIGIN; set PRODUCT."; exit 1; }
for c in storefront-web storefront-web-live storefront-web-before storefront-web-after; do
  if [ -n "$(docker ps -q -f "name=^${c}$")" ]; then echo "Another web pipeline is running ($c). Wait for it, then run this again."; exit 1; fi
done
for p in 3000 3001; do
  if curl -sf -o /dev/null "http://localhost:$p/"; then echo "Something is already serving http://localhost:$p. Stop it first."; exit 1; fi
done

cleanup() { docker rm -f storefront-web-before storefront-web-after >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

# The before tree: git archive of the commit, with this tree's node_modules copied in (same lockfile, checked above).
BEFORE_DIR="$HOME/.cache/storefront-ab/$BEFORE_SHA"
if [ ! -d "$BEFORE_DIR/web/node_modules" ]; then
  rm -rf "$BEFORE_DIR"; mkdir -p "$BEFORE_DIR"
  git archive "$BEFORE_SHA" | tar -x -C "$BEFORE_DIR"
  cp -a web/node_modules "$BEFORE_DIR/web/node_modules"
fi
declare -A DIR=([before]="$BEFORE_DIR" [after]="$REPO")

mkdir -p "$CACHE"
for v in before after; do
  echo "build $v (${DIR[$v]})"
  rm -rf "${DIR[$v]}/web/.next"
  docker run --rm --network host --user "$UIDGID" -e HOME=/tmp/home -e npm_config_cache=/npm-cache -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
    -v "${DIR[$v]}":/workspace -v "$CACHE":/npm-cache -w /workspace/web "$NODE_IMAGE" npm run build >/dev/null
  docker run -d --name "storefront-web-$v" --network host --user "$UIDGID" -e HOME=/tmp/home -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
    -v "${DIR[$v]}":/workspace -w /workspace/web "$NODE_IMAGE" npx next start -p "${PORT[$v]}" >/dev/null
done
for v in before after; do
  for _ in $(seq 1 30); do curl -sf -o /dev/null "http://localhost:${PORT[$v]}/" && break; sleep 1; done
  # Warm each page once so the first measured run does not pay for Next's first render of the route.
  for page in $PAGES; do curl -sf -o /dev/null "http://localhost:${PORT[$v]}${PATHS[$page]}" || { echo "$v: ${PATHS[$page]} did not answer 200"; exit 1; }; done
done

for v in before after; do rm -rf "evidence-raw/lighthouse/$LABEL-$v"; mkdir -p "evidence-raw/lighthouse/$LABEL-$v"; done
for i in $(seq 1 "$RUNS"); do
  order="before after"; [ $((i % 2)) -eq 0 ] && order="after before"
  for page in $PAGES; do
    for ff in mobile desktop; do
      preset=""; [ "$ff" = desktop ] && preset="--preset=desktop"
      for v in $order; do
        echo "run $i $page $ff $v"
        docker run --rm --network host --ipc=host --user "$UIDGID" -e HOME=/tmp/home -e npm_config_cache=/npm-cache \
          -e CHROME_PATH="$CHROME" -v "$REPO":/workspace -v "$CACHE":/npm-cache -w /workspace "$PW_IMAGE" \
          npx -y "lighthouse@$LH_VERSION" "http://localhost:${PORT[$v]}${PATHS[$page]}" $preset --quiet \
            --chrome-flags="--headless=new --no-sandbox" \
            --only-categories=performance,accessibility,best-practices,seo \
            --output=json --output-path="evidence-raw/lighthouse/$LABEL-$v/$page-$ff-$i.json" >/dev/null
      done
    done
  done
done

python3 -I scripts/lighthouse-compare.py "$LABEL" "evidence-raw/lighthouse/$LABEL-before" "evidence-raw/lighthouse/$LABEL-after" \
  "$BEFORE_SHA" "$AFTER_DESC" "$(uname -n), $(nproc) CPUs" "$API_ORIGIN" "$PRODUCT" > "docs/evidence/lighthouse-$LABEL.md"
cat "docs/evidence/lighthouse-$LABEL.md"
