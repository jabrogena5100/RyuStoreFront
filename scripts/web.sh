#!/usr/bin/env bash
# Run Node tooling for the frontend (web/) in a pinned Docker image, so the VM needs no Node install.
# The npm cache lives on the host so repeated runs are fast. Host networking avoids the slow default bridge.
#
#   scripts/web.sh npm install
#   scripts/web.sh npm run build
#   scripts/web.sh npx tsc --noEmit
set -euo pipefail

NODE_IMAGE="${NODE_IMAGE:-node:26-bookworm-slim}"   # Node 26 is LTS ("Lithium") as of 2026-11-15
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${NPM_CACHE_DIR:-$HOME/.cache/storefront-npm}"
mkdir -p "$CACHE"

exec docker run --rm --network host \
  --user "$(id -u):$(id -g)" \
  -e HOME=/tmp/home -e npm_config_cache=/npm-cache -e NPM_CONFIG_UPDATE_NOTIFIER=false \
  -e NEXT_TELEMETRY_DISABLED=1 \
  -v "$REPO_ROOT":/workspace -v "$CACHE":/npm-cache \
  -w /workspace/web \
  "$NODE_IMAGE" "$@"
