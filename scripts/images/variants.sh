#!/usr/bin/env bash
# Build the responsive variants (AVIF and WebP at 480/960/1440 px) of every image in content/catalogue/manifest.json.
# Needs only Docker: pinned Node image, sharp from scripts/images/package.json. Reads evidence-raw/images/originals.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
CACHE="${NPM_CACHE_DIR:-$HOME/.cache/storefront-npm}"
mkdir -p "$CACHE"
exec docker run --rm --network host --user "$(id -u):$(id -g)" -e HOME=/tmp/home -e npm_config_cache=/npm-cache \
  -v "$PWD":/workspace -v "$CACHE":/npm-cache -w /workspace node:26-bookworm-slim \
  sh -c "npm install --prefix scripts/images --no-audit --no-fund --silent && node scripts/images/variants.mjs"
