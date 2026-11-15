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

## Session stage, slice 1: network and database (`infra/session/`)

Status: **applied once and checked (see `docs/verification.md`); destroy not yet run or audited.** Contents: VPC (10.20.0.0/16), two public subnets, internet gateway,
three security groups (ALB, app, database), and a single-AZ `db.t4g.micro` Postgres 18.6 whose password RDS keeps in Secrets Manager.
Later slices add ECS, the ALB, ACM and the DNS record.

```
export AWS_PROFILE=ryustorefront
BUCKET=storefront-tfstate-$(aws sts get-caller-identity --query Account --output text)
scripts/aws-terraform.sh -chdir=infra/session init -backend-config="bucket=$BUCKET"
scripts/aws-terraform.sh -chdir=infra/session validate
scripts/aws-terraform.sh -chdir=infra/session plan -out=tfplan
# read the whole plan, then:
scripts/aws-terraform.sh -chdir=infra/session apply tfplan
```

Destroy when the session ends (never touches `foundation/`):

```
scripts/aws-terraform.sh -chdir=infra/session plan -destroy -out=tfplan
scripts/aws-terraform.sh -chdir=infra/session apply tfplan
```

## Session stage, slice 2: the API behind HTTPS (`infra/session/` plus new `foundation/` resources)

Status: **foundation part applied and checked (certificate issued, secret shells created); the session part is written but not yet planned or applied.** Adds to `foundation/`: two empty Stripe secret shells and the ACM certificate for
`api.ryustorefront.net` with its validation records. Adds to `session/`: IAM roles, log group, ECS cluster, one Fargate task definition and service
(0.5 vCPU / 1 GB, API only, no worker yet), an ALB (HTTP redirects to HTTPS), the DNS alias, and a start-up seed of 100 products.

Order matters (a task cannot start while a secret has no value):

1. Push an image from a clean `main`: `scripts/ecr-push.sh` (note the tag it prints).
2. Foundation: `plan -out=tfplan`, read it, `apply` (expect the certificate to take a few minutes to validate).
3. Load the Stripe key from `.env` into the shell and into Secrets Manager (the value never prints):
   ```
   set -a; . ./.env; set +a
   printf %s "$STRIPE_SECRET_KEY" | aws secretsmanager put-secret-value --region us-west-2 \
     --secret-id storefront/stripe-secret-key --secret-string file:///dev/stdin
   ```
   The webhook secret is loaded only after step 6 (it comes from the Stripe dashboard endpoint).
   Until then put a placeholder so the task can start: `printf %s placeholder | aws secretsmanager put-secret-value ... --secret-id storefront/stripe-webhook-secret --secret-string file:///dev/stdin`.
4. Session: `plan -out=tfplan -var image_tag=<tag>` (service created with 0 tasks), read, `apply`.
5. Start the task: `plan -out=tfplan -var image_tag=<tag> -var desired_count=1`, read, `apply`.
6. In the Stripe dashboard (test mode) add an endpoint `https://api.ryustorefront.net/api/stripe/webhook` for the four events
   `checkout.session.completed`, `.expired`, `.async_payment_succeeded`, `.async_payment_failed`. Copy its signing secret into
   `storefront/stripe-webhook-secret` (same `put-secret-value` form), then restart the task:
   `aws ecs update-service --cluster storefront --service api --force-new-deployment --region us-west-2`.
7. `BASE=https://api.ryustorefront.net bash scripts/manual-checkout.sh <sku>`, pay with card 4242, then check the order in the database logs/API.

Destroy of the session stage is unchanged; the secrets, certificate and zone stay.
