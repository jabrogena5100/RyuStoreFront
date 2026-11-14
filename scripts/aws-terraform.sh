#!/usr/bin/env bash
# Run Terraform inside a pinned Docker image using short-lived credentials
# exported from an `aws login` session. Credentials are written to a mode-600
# temp file that is removed on exit; they are never echoed.
#
# Usage:
#   export AWS_PROFILE=ryustorefront
#   aws login --profile "$AWS_PROFILE"        # browser sign-in with MFA (add --remote on a headless VM)
#   scripts/aws-terraform.sh version
#   scripts/aws-terraform.sh -chdir=infra plan -out=tfplan
#
# Override the pinned version with TF_VERSION=... (default below).
set -euo pipefail

TF_VERSION="${TF_VERSION:-1.17.2}"
IMAGE="hashicorp/terraform:${TF_VERSION}"
PROFILE="${AWS_PROFILE:-}"

if [[ -z "$PROFILE" ]]; then
  echo "AWS_PROFILE is not set (expected: export AWS_PROFILE=ryustorefront)" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ENV_FILE="$(mktemp)"
chmod 600 "$ENV_FILE"
trap 'rm -f "$ENV_FILE"' EXIT

if ! aws configure export-credentials --profile "$PROFILE" --format env-no-export > "$ENV_FILE" 2>/dev/null; then
  echo "Could not export credentials for profile '$PROFILE'. Run: aws login --profile $PROFILE" >&2
  exit 1
fi

echo "terraform ${TF_VERSION} via Docker, profile ${PROFILE}" >&2

exec docker run --rm \
  --network host \
  --user "$(id -u):$(id -g)" \
  --env-file "$ENV_FILE" \
  -e AWS_REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-us-west-2}}" \
  -e TF_IN_AUTOMATION=1 \
  -v "$REPO_ROOT":/workspace \
  -w /workspace \
  "$IMAGE" "$@"
