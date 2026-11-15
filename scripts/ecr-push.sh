#!/usr/bin/env bash
# Build the image on this VM and push it to ECR, tagged with the commit SHA.
# Repository tags are immutable, so a SHA is pushed once; re-running skips the push if the tag is already there.
#
# Usage:
#   export AWS_PROFILE=ryustorefront
#   aws login --profile "$AWS_PROFILE"
#   scripts/ecr-push.sh
set -euo pipefail

REGION="${AWS_REGION:-us-west-2}"
REPO="storefront"
PROFILE="${AWS_PROFILE:-}"
[[ -n "$PROFILE" ]] || { echo "AWS_PROFILE is not set (expected: export AWS_PROFILE=ryustorefront)" >&2; exit 1; }

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# The tag must describe what was built, so build only from a clean, committed tree.
if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree is not clean; commit or stash first so the SHA tag is honest." >&2
  exit 1
fi
TAG="$(git rev-parse --short=12 HEAD)"

ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT}.dkr.ecr.${REGION}.amazonaws.com"
IMAGE="${REGISTRY}/${REPO}:${TAG}"

if aws ecr describe-images --repository-name "$REPO" --image-ids imageTag="$TAG" --region "$REGION" >/dev/null 2>&1; then
  echo "ECR already has ${REPO}:${TAG}; nothing to push (tags are immutable)." >&2
  exit 0
fi

DOCKER_BUILDKIT=1 docker build --network host -t "$IMAGE" .
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"
docker push "$IMAGE"
echo "Pushed ${IMAGE}" >&2
