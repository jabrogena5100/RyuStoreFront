# Infrastructure

Terraform runs through `scripts/aws-terraform.sh` (pinned image, short-lived credentials from `aws login`).
Stages are applied separately so the per-session one can be destroyed without touching DNS or the registry.

| Stage | Contents | Lifetime |
| --- | --- | --- |
| `foundation/` | Route 53 zone for `ryustorefront.net`, ECR repository | Kept between sessions |
| `session/` (not written yet) | VPC, RDS, ECS, ALB, ACM, Secrets Manager | Created and destroyed per session |

Status: **written, not yet applied or verified.** Results go in `docs/verification.md` only after real output.

## One-time: state bucket (not managed by Terraform)

```
export AWS_PROFILE=ryustorefront AWS_REGION=us-west-2
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
BUCKET=storefront-tfstate-$ACCOUNT
aws s3api create-bucket --bucket "$BUCKET" --region us-west-2 \
  --create-bucket-configuration LocationConstraint=us-west-2
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
```

## Foundation stage

```
scripts/aws-terraform.sh -chdir=infra/foundation init -backend-config="bucket=$BUCKET"
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name ryustorefront.net \
  --query 'HostedZones[0].Id' --output text | sed 's|/hostedzone/||')
scripts/aws-terraform.sh -chdir=infra/foundation import aws_route53_zone.main "$ZONE_ID"
scripts/aws-terraform.sh -chdir=infra/foundation plan -out=tfplan
# read the plan: expect the zone unchanged (or tags only) and the ECR repository plus lifecycle policy added
scripts/aws-terraform.sh -chdir=infra/foundation apply tfplan
```

If `list-hosted-zones-by-name` shows more than one zone for the domain, stop: keep only the one whose
name servers match the Squarespace delegation (`ns-96.awsdns-12.com` and the other three) and delete the empty duplicate.

## Image (Dockerfile at the repo root)

One image, two services: the API as-is, the worker with `SPRING_PROFILES_ACTIVE=worker`.
Status: **built and run locally once (see `docs/verification.md`); not yet pushed to ECR or run in AWS.**

Build and run locally against the Compose Postgres (the `.env` file holds the database password):

```
docker compose up -d postgres mailpit
docker build --network host -t storefront:local .
set -a; . ./.env; set +a
docker run --rm --network host \
  -e SPRING_DATASOURCE_PASSWORD="$POSTGRES_PASSWORD" \
  storefront:local
# in another terminal:
curl -s localhost:8080/actuator/health
docker run --rm --network host --entrypoint id storefront:local     # expect uid=10001, not root
```

Push (needs a clean, committed tree; tags are immutable, so each commit SHA is pushed once):

```
export AWS_PROFILE=ryustorefront
scripts/ecr-push.sh
```
