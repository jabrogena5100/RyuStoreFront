#!/usr/bin/env bash
# Manual end-to-end helper for the Stripe check: registers a throwaway customer, puts one unit of a variant in
# the cart, checks out, starts payment, and prints the Stripe payment page URL to open in a browser.
# Needs the API running on localhost:8080 (or BASE=...), curl and jq. Stripe test mode only.
#
#   bash scripts/manual-checkout.sh <sku>
#
# The session is kept in $JAR so you can look at the cart afterwards:
#   curl -s -b "$JAR" http://localhost:8080/api/cart | jq
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
JAR="${JAR:-/tmp/storefront-manual.cookies}"
SKU="${1:?usage: bash scripts/manual-checkout.sh <sku>}"
EMAIL="manual-$(date +%s)@example.com"
PASSWORD="correct horse battery"
JSON='Content-Type: application/json'

rm -f "$JAR"
touch "$JAR" && chmod 600 "$JAR"

TOKEN="$(curl -fsS -b "$JAR" -c "$JAR" "$BASE/api/auth/csrf" | jq -r .token)"

# register signs the new customer in and returns a new CSRF token (the session id rotates)
TOKEN="$(curl -fsS -b "$JAR" -c "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -H "$JSON" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" "$BASE/api/auth/register" | jq -r .csrf.token)"

curl -fsS -b "$JAR" -c "$JAR" -X PUT -H "X-CSRF-TOKEN: $TOKEN" -H "$JSON" \
  -d '{"quantity":1}' "$BASE/api/cart/items/$SKU" > /dev/null

ORDER="$(curl -fsS -b "$JAR" -c "$JAR" -H "X-CSRF-TOKEN: $TOKEN" -H "$JSON" \
  -H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
  -d '{"shippingAddress":{"name":"Ada Buyer","line1":"1 Main St","city":"Portland","region":"OR","postalCode":"97201","country":"US"}}' \
  "$BASE/api/checkout" | jq -r .id)"

URL="$(curl -fsS -b "$JAR" -c "$JAR" -X POST -H "X-CSRF-TOKEN: $TOKEN" \
  "$BASE/api/orders/$ORDER/pay" | jq -r .url)"

echo "customer: $EMAIL"
echo "order:    $ORDER"
echo
echo "Open this in a browser and pay with test card 4242 4242 4242 4242 (any future expiry, any CVC, any ZIP):"
echo "$URL"
