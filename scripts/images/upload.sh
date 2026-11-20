#!/usr/bin/env bash
# Step 5 of the image pipeline: upload the variants to object storage.
#
#   scripts/images/upload.sh local                       # SeaweedFS from compose.yaml (http://localhost:8333), bucket storefront-images
#   AWS_PROFILE=ryustorefront IMAGES_BUCKET=<bucket> scripts/images/upload.sh aws     # real S3 (phase 6; bucket from Terraform)
#
# Objects: <key>-<width>.<avif|webp> from evidence-raw/images/variants, with an explicit Content-Type and
# "Cache-Control: public, max-age=31536000, immutable" (keys contain the photo's hash, so a changed photo gets a new key).
# Runs the pinned AWS CLI in Docker. For "aws" the short-lived `aws login` credentials are passed through a mode-600 temp file
# that is removed on exit; they are never printed.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.."
TARGET="${1:?usage: scripts/images/upload.sh local|aws}"
CLI_IMAGE="amazon/aws-cli:2.39.6"
SRC="evidence-raw/images/variants"
[ -d "$SRC/products" ] || { echo "No variants in $SRC; run scripts/images/variants.sh first." >&2; exit 1; }

ENV_FILE="$(mktemp)"; chmod 600 "$ENV_FILE"; trap 'rm -f "$ENV_FILE"' EXIT
case "$TARGET" in
  local)
    BUCKET="storefront-images"
    ENDPOINT=(--endpoint-url http://localhost:8333)
    # SeaweedFS runs without identities locally, so any credentials are accepted; these are placeholders, not secrets.
    printf 'AWS_ACCESS_KEY_ID=local\nAWS_SECRET_ACCESS_KEY=local\nAWS_REGION=us-west-2\n' > "$ENV_FILE"
    ;;
  aws)
    BUCKET="${IMAGES_BUCKET:?set IMAGES_BUCKET (terraform output)}"
    ENDPOINT=()
    : "${AWS_PROFILE:?set AWS_PROFILE (ryustorefront)}"
    aws configure export-credentials --profile "$AWS_PROFILE" --format env-no-export > "$ENV_FILE" \
      || { echo "Could not export credentials; run: aws login --profile $AWS_PROFILE" >&2; exit 1; }
    echo "AWS_REGION=${AWS_REGION:-us-west-2}" >> "$ENV_FILE"
    ;;
  *) echo "target must be local or aws" >&2; exit 1 ;;
esac

aws() {
  docker run --rm --network host --user "$(id -u):$(id -g)" -e HOME=/tmp --env-file "$ENV_FILE" \
    -v "$PWD/$SRC":/variants:ro "$CLI_IMAGE" "$@"
}

if [ "$TARGET" = local ] && ! aws "${ENDPOINT[@]}" s3api head-bucket --bucket "$BUCKET" >/dev/null 2>&1; then
  aws "${ENDPOINT[@]}" s3api create-bucket --bucket "$BUCKET" >/dev/null
  echo "created bucket $BUCKET"
fi

for fmt in avif webp; do
  aws "${ENDPOINT[@]}" s3 sync /variants "s3://$BUCKET/" --only-show-errors \
    --exclude "*" --include "*.$fmt" --content-type "image/$fmt" \
    --cache-control "public, max-age=31536000, immutable"
done
COUNT="$(find "$SRC" -type f \( -name '*.avif' -o -name '*.webp' \) | wc -l)"
BYTES="$(find "$SRC" -type f \( -name '*.avif' -o -name '*.webp' \) -printf '%s\n' | awk '{s+=$1} END {print s}')"
echo "synced $COUNT files ($((BYTES / 1024)) KB) to s3://$BUCKET/ ($TARGET)"
