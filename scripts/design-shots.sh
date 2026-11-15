#!/usr/bin/env bash
# Rebuild the frontend, serve it, take screenshots of every design-review screen at desktop and phone width,
# and run an axe (WCAG A/AA) scan on each. Everything runs in Docker; nothing is installed on the VM.
#
#   scripts/design-shots.sh
#
# Output: design/screens/*.png and design/screens/AXE.txt (a one-line-per-page summary of the scan).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO="$PWD"
UIDGID="$(id -u):$(id -g)"
PW_IMAGE="${PW_IMAGE:-mcr.microsoft.com/playwright:v1.65.1-noble}"   # must match @playwright/test in web/package.json

# Both web pipelines use port 3000, web/.next and web/test-results; running them at once corrupts both.
for c in storefront-web storefront-web-live; do
  if [ -n "$(docker ps -q -f "name=^${c}$")" ]; then echo "Another web pipeline is running ($c). Wait for it, then run this again."; exit 1; fi
done
if curl -sf -o /dev/null http://localhost:3000/; then echo "Something is already serving http://localhost:3000. Stop it first."; exit 1; fi

cleanup() { docker rm -f storefront-web storefront-mock-api >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

rm -rf design/screens web/test-results
mkdir -p design/screens

# The catalogue screens read /api/products; the screenshots run against a mock of those endpoints (web/e2e/mock-api.mjs),
# not the real API.
# Its own port, so a real API on 8080 (for example `./mvnw spring-boot:run`) can run at the same time and is never used here.
MOCK_PORT="${MOCK_API_PORT:-8089}"
export API_ORIGIN="http://localhost:$MOCK_PORT"
if curl -sf -o /dev/null "$API_ORIGIN/api/categories"; then echo "Port $MOCK_PORT is already in use; set MOCK_API_PORT."; exit 1; fi
docker run -d --name storefront-mock-api --network host --user "$UIDGID" -e HOME=/tmp/home -e MOCK_API_PORT="$MOCK_PORT" \
  -v "$REPO":/workspace -w /workspace/web node:26-bookworm-slim node e2e/mock-api.mjs >/dev/null
for _ in $(seq 1 30); do curl -sf -o /dev/null "$API_ORIGIN/api/categories" && break; sleep 1; done
# Prove the mock (not another server) answers: it serves 76 fixture products.
curl -sf "$API_ORIGIN/api/products?pageSize=1" | grep -q '"total":76' || { echo "The mock API is not answering on $MOCK_PORT."; exit 1; }

# Start from a clean build: Next keeps fetched API responses in web/.next/cache, and a stale entry from an earlier run
# (for example the mock API) would be served once after its 30 s expiry.
rm -rf web/.next
scripts/web.sh npm run build
docker run -d --name storefront-web --network host --user "$UIDGID" -e HOME=/tmp/home -e NEXT_TELEMETRY_DISABLED=1 -e API_ORIGIN \
  -v "$REPO":/workspace -w /workspace/web node:26-bookworm-slim npm run start >/dev/null
for _ in $(seq 1 30); do curl -sf -o /dev/null http://localhost:3000/ && break; sleep 1; done

set +e
docker run --rm --network host --ipc=host --user "$UIDGID" -e HOME=/tmp/home \
  -v "$REPO":/workspace -w /workspace/web "$PW_IMAGE" npx playwright test
status=$?
set -e

python3 -I - <<'PY' > design/screens/AXE.txt
import glob, json, os
files = sorted(glob.glob("web/test-results/axe/*.json"))
total = 0
print("axe (WCAG 2.0/2.1 A and AA) per page; 0 means no violations found by the scan")
for f in files:
    v = json.load(open(f))
    total += len(v)
    ids = ", ".join(sorted({x["id"] for x in v})) or "none"
    print(f"{os.path.basename(f)[:-5]:34s} {len(v)} violations ({ids})")
print(f"pages scanned: {len(files)}   total violations: {total}")
PY
tail -1 design/screens/AXE.txt
exit "$status"
