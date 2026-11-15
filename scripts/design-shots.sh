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

cleanup() { docker rm -f storefront-web >/dev/null 2>&1 || true; }
trap cleanup EXIT
cleanup

rm -rf design/screens web/test-results
mkdir -p design/screens

scripts/web.sh npm run build
docker run -d --name storefront-web --network host --user "$UIDGID" -e HOME=/tmp/home -e NEXT_TELEMETRY_DISABLED=1 \
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
