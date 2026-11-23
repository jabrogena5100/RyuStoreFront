# Infrastructure: rebuild from zero after destroy

This runbook takes you from the normal resting state (session destroyed, foundation kept) to a running cloud shop and back,
and from a completely empty AWS account if you ever need it. Every number here has a status: **observed** (seen in pasted
Terraform or AWS output; see `docs/metrics.md` and `docs/verification.md`), **derived** (worked out from the code, never
observed as one run), or **estimate** (list price or guess, not measured).

Region `us-west-2`, AWS profile `ryustorefront`, Terraform 1.17.2 in Docker through `scripts/aws-terraform.sh`, AWS provider 6.x.

## 1. What exists, and what survives a destroy

| Layer | Contents | Created by | Survives `session` destroy | Monthly cost when idle |
| --- | --- | --- | --- | --- |
| Registrar and DNS for the apex | `ryustorefront.com`, registered at Cloudflare, apex DNS on Cloudflare | Hand (Cloudflare dashboard) | Yes | Domain renewal only |
| Delegation of `api.` | Four `NS` records named `api` in Cloudflare pointing at the Route 53 zone | Hand (Cloudflare dashboard) | Yes | none |
| State bucket | `storefront-tfstate-<account>` (versioned, public access blocked) | Hand (AWS CLI, section 5.1) | Yes | cents (estimate) |
| Route 53 zone `api.ryustorefront.com` | Zone `Z06707493A3HFUP7647H3` | Hand, then imported into `foundation` (`prevent_destroy`) | Yes | $0.50 (list price, estimate) |
| `infra/foundation` | ECR repository `storefront` and lifecycle policy (keeps 15 images); Secrets Manager shells `storefront/stripe-secret-key` and `storefront/stripe-webhook-secret`; ACM certificate for `api.ryustorefront.com` and its validation record | Terraform | Yes | Secrets $0.40 each, ECR $0.10/GB-month stored (list prices, estimate) |
| Stripe (test mode) | Webhook endpoint `https://api.ryustorefront.com/api/stripe/webhook` and its signing secret | Hand (Stripe dashboard) | Yes | free |
| `infra/foundation` (phase 6 additions) | ECR repository `storefront-web`; ACM certificate for the apex `ryustorefront.com` (validated by a CNAME made by hand in Cloudflare); private S3 bucket `storefront-images-<account>` behind a CloudFront distribution (origin access control, `*.cloudfront.net` name) and the SSM parameter `/storefront/images-base-url`; SES domain identity `api.ryustorefront.com` with three DKIM records and a `p=none` DMARC record in the zone | Terraform (`web.tf`, `images.tf`, `email.tf`) | Yes | S3 about 45 MiB, CloudFront and SES pay per use, SSM standard parameter free (list prices, estimate) |
| Outside Terraform, phase 6 | Apex record in Cloudflare: `ryustorefront.com` CNAME `api.ryustorefront.com` (DNS only, flattened); the apex certificate's validation CNAME in Cloudflare; the owner's own address verified in SES (sandbox recipient) | Hand (Cloudflare dashboard, AWS CLI) | Yes | none |
| `infra/session` | VPC, 2 public subnets, internet gateway, route table, 3 security groups and 5 rules, DB subnet group, RDS Postgres 18.6 (`db.t4g.micro`, single-AZ) with its RDS-managed password secret, IAM roles, log group, ECS cluster, task definition, service, ALB, target group, two listeners, DNS alias `api`; since phase 6 also the worker and web task definitions and services, their log groups, the web target group, the apex certificate on the HTTPS listener, the host-header rule for `ryustorefront.com`, two security-group rules for port 3000, the SES send policy and the web task role | Terraform | **No: destroyed after every session** | 0 when destroyed |

Foundation total when idle: about **$1 to $2 a month** (estimate from list prices: zone $0.50 + two secrets $0.80 + ECR storage;
not yet checked in Cost Explorer, `docs/metrics.md` C9).

### Why destroy only the session stage

The session holds everything that costs money by the hour (RDS, ALB, Fargate). The foundation costs about $1 to $2 a month and
holds the things that are slow, manual or fragile to recreate:

- **DNS delegation.** A new Route 53 zone gets new name servers. The four `NS` records in Cloudflare would have to be edited by hand and
  checked against Cloudflare's own name server before anything resolves.
- **The certificate.** A new one waits for DNS validation (about 30 s observed when the zone already works; longer if delegation is
  still propagating).
- **The secrets.** Deleted secrets sit in a 30-day recovery window, and a secret with the same name cannot be created until it is
  restored or force-deleted (section 6).
- **The image registry.** Deleting the repository deletes every pushed image; the next session needs a fresh build and push.

So the normal cycle is section 3 (start a session) and section 4 (end it). Sections 5 and 6 are for a fresh account or a full teardown.

## 2. Before every working session

```
export AWS_PROFILE=ryustorefront AWS_REGION=us-west-2
aws login --profile "$AWS_PROFILE"            # add --remote on a headless VM
BUCKET=storefront-tfstate-$(aws sts get-caller-identity --query Account --output text)
```

`scripts/aws-terraform.sh` copies the `aws login` credentials into the container. They are short-lived (about 15 minutes in the
RyuCloud runbook), so **run `aws login` again immediately before each `apply`**, not only at the start. An apply that loses its
credentials midway leaves `errored.tfstate` (section 7).

Always save the plan, read it, then apply that file. Never `apply` without a plan file.

## 3A. One-time setup for the full shop (phase 6: frontend, worker, photos, e-mail)

Do this once; it survives every session destroy. Nothing here has been run yet.

**3A.1 Apply the foundation additions.**

```
aws login --profile "$AWS_PROFILE"
scripts/aws-terraform.sh -chdir=infra/foundation init -backend-config="bucket=$BUCKET"
scripts/aws-terraform.sh -chdir=infra/foundation plan -out=tfplan
```
Expect **`Plan: 16 to add, 0 to change, 0 to destroy.`** (derived from the code: ECR repository and lifecycle policy 2, apex
certificate 1, bucket and its public-access block, ownership, encryption and policy 5, origin access control 1, distribution 1,
SSM parameter 1, SES identity 1, DKIM records 3, DMARC record 1). Read it: nothing may change or be destroyed. Then
`aws login` again and `scripts/aws-terraform.sh -chdir=infra/foundation apply tfplan`. The CloudFront distribution takes several
minutes (not measured here).

**3A.2 Apex certificate (Cloudflare, by hand).**

```
scripts/aws-terraform.sh -chdir=infra/foundation output -json app_certificate_validation \
  | jq -r '.[] | "Name:  \(.name | rtrimstr(".") | rtrimstr(".ryustorefront.com"))\nType:  \(.type)\nValue: \(.value | rtrimstr("."))"'
```
(Without `-json`, Terraform prints the list of objects in its own format, which is hard to copy. The wrapper's own status line goes
to stderr, so the pipe sees clean JSON. If it says the output does not exist, 3A.1's apply has not finished.) In Cloudflare, DNS
for `ryustorefront.com`: add that record as **CNAME, Proxy status DNS only (grey cloud)**. The script already removes the trailing
dots and the `.ryustorefront.com` suffix from the name, as Cloudflare expects. Then wait for `ISSUED`:

```
aws acm list-certificates --query "CertificateSummaryList[?DomainName=='ryustorefront.com'].Status" --output text
```

**3A.3 Apex record (Cloudflare, by hand).** Add `ryustorefront.com` (name `@`) **CNAME `api.ryustorefront.com`, DNS only**. Cloudflare
flattens a CNAME at the apex, so it answers with the load balancer's addresses; because Terraform re-points `api` at each new load
balancer, this record never needs editing. Delete any other `@` A/AAAA record first (they conflict). Between sessions the apex does
not resolve, which is expected. Unverified until a session is up (check in 3.5).

**3A.4 SES.** The domain identity verifies itself through the DKIM records (minutes to hours; not measured):

```
aws sesv2 get-email-identity --email-identity api.ryustorefront.com --query '[VerifiedForSendingStatus,DkimAttributes.Status]' --output text
# expect: True    SUCCESS
```
The account is in the SES **sandbox**: mail is delivered only to verified addresses. Verify the address you will register with in
the shop (AWS sends a link to it; click it). Keep the address out of committed files:

```
read -r MY_EMAIL                                   # type your address
aws sesv2 create-email-identity --email-identity "$MY_EMAIL"
aws sesv2 get-email-identity --email-identity "$MY_EMAIL" --query VerifiedForSendingStatus --output text    # True after the click
```

**3A.5 Upload the photos** (about 45 MiB, 1,128 files, from `evidence-raw/images/variants`; regenerate them first with
`scripts/images/variants.sh` if that folder is gone):

```
aws login --profile "$AWS_PROFILE"
IMAGES_BUCKET=$(scripts/aws-terraform.sh -chdir=infra/foundation output -raw images_bucket) scripts/images/upload.sh aws
CDN=$(scripts/aws-terraform.sh -chdir=infra/foundation output -raw images_base_url)
curl -sI "$CDN/products/ostrava-atelier-umber-chelsea-boot-1-9b06e0e4-480.avif" | grep -iE '^HTTP|content-type|cache-control|x-cache'
# expect: HTTP/2 200, image/avif, public, max-age=31536000, immutable
curl -s -o /dev/null -w '%{http_code}\n' "https://$IMAGES_BUCKET.s3.us-west-2.amazonaws.com/products/ostrava-atelier-umber-chelsea-boot-1-9b06e0e4-480.avif"
# expect: 403 (the bucket itself is private)
```

## 3. Start a session (foundation already exists)

Normal path. Duration: about 10 minutes of waiting, mostly RDS (estimate; the full sequence has never been timed in one go, `docs/metrics.md` C10).

**3.1 Images.** From a clean, committed tree (the script refuses otherwise; tags are the commit SHA and immutable):

```
scripts/ecr-push.sh                 # API and worker. Prints: Pushed <account>.dkr.ecr.us-west-2.amazonaws.com/storefront:<tag>
scripts/ecr-push.sh web             # frontend, built with API_ORIGIN=https://api.ryustorefront.com -> storefront-web:<tag>
TAG=<the 12-character tag it printed>   # the same commit, so the same tag for both
```
If ECR already has that tag the script says so and exits; reuse the tag.

**3.2 Check the foundation is intact** (should show no changes):

```
scripts/aws-terraform.sh -chdir=infra/foundation init -backend-config="bucket=$BUCKET"
scripts/aws-terraform.sh -chdir=infra/foundation plan -out=tfplan
```
Expect: `No changes. Your infrastructure matches the configuration.` Anything else: read it and stop if it wants to replace the zone or the certificate.

**3.3 Secrets.** They persist between sessions. Check both have a current value (prints names and dates, never values):

```
for s in storefront/stripe-secret-key storefront/stripe-webhook-secret; do
  aws secretsmanager describe-secret --secret-id "$s" --query '[Name,LastChangedDate]' --output text
done
```
A task cannot start if a secret has no value. If either is missing, load it as in section 5.5.

**3.4 Session plan and apply.**

```
scripts/aws-terraform.sh -chdir=infra/session init -backend-config="bucket=$BUCKET"
scripts/aws-terraform.sh -chdir=infra/session plan -out=tfplan -var image_tag=$TAG -var web_image_tag=$TAG \
  -var desired_count=1 -var worker_desired_count=1 -var web_desired_count=1
```
Expect: **`Plan: 44 to add, 0 to change, 0 to destroy.`** (the 31 observed on 2026-11-15 plus 13 for phase 6: two log
groups, worker and web task definitions and services, web target group, listener certificate, listener rule, two security-group
rules, SES policy, web task role; 44 added was observed on 2026-11-23, `Apply complete! Resources: 44 added`). Also fails early if the apex certificate is not `ISSUED` (3A.2) or the SSM parameter is missing (3A.1).
The plan fails early, before creating anything, if a data source is missing: the zone `api.ryustorefront.com`, an `ISSUED`
certificate for that name, the two secrets or the ECR repository. Fix the foundation first.

```
aws login --profile "$AWS_PROFILE"
scripts/aws-terraform.sh -chdir=infra/session apply tfplan
```
Observed durations (single applies): RDS 5 min 21 s, ALB 2 min 25 s, DNS record 33 s. They overlap in one apply.

**3.5 Wait for a healthy task.** The API's first start took 55.2 s on 0.5 vCPU (observed once, cold, including migrations and the
seed). The service allows 180 s before health checks count.

```
aws ecs describe-services --cluster storefront --services api \
  --query 'services[0].[desiredCount,runningCount,deployments[0].rolloutState]' --output text     # expect: 1 1 COMPLETED
aws logs tail /storefront/api --since 10m | grep -E 'Started StorefrontApplication|Seeded|ERROR'
curl -s -o /dev/null -w '%{http_code} %{ssl_verify_result}\n' https://api.ryustorefront.com/actuator/health   # expect: 200 0
```

**3.5b The full shop (phase 6).** Worker and web services, the apex, and photos through CloudFront:

```
aws ecs describe-services --cluster storefront --services api worker web \
  --query 'services[].[serviceName,desiredCount,runningCount,deployments[0].rolloutState]' --output text   # 1 1 COMPLETED each
aws logs tail /storefront/worker --since 10m | grep -E 'Started StorefrontApplication|ERROR'
dig +short ryustorefront.com @$(dig +short NS ryustorefront.com | head -1)       # the ALB's addresses, from Cloudflare itself
curl -s -o /dev/null -w '%{http_code} %{ssl_verify_result}\n' https://ryustorefront.com/healthz             # expect: 200 0
curl -s https://ryustorefront.com/api/products/ostrava-atelier-umber-chelsea-boot | jq -r '.images[0].base'  # the CloudFront URL
scripts/web-cloud-check.sh          # the 19 live checks against https://ryustorefront.com; screenshots to docs/evidence/cloud-shots/
```

**3.5c One real purchase in the browser** at `https://ryustorefront.com`: register with the address verified in 3A.4, add a pair, check
out, pay with card 4242 4242 4242 4242. Expect: Stripe returns you to `/orders/<id>/confirmation`, the cart empties within a few
seconds (worker), and the confirmation mail arrives from `orders@api.ryustorefront.com`. Evidence to keep:

```
aws logs tail /storefront/worker --since 15m | grep -E 'Sent confirmation|ERROR|WARN'
aws logs tail /storefront/api --since 15m | grep -iE 'webhook|checkout.session'
```

**3.6 One checkout from the command line** (phase 4b; still works). Pick a SKU from the seeded catalogue, then pay with card 4242 4242 4242 4242 on Stripe's page:

```
curl -s 'https://api.ryustorefront.com/api/products?pageSize=1' | jq -r '.items[0].slug' \
  | xargs -I{} curl -s https://api.ryustorefront.com/api/products/{} | jq -r '.variants[] | select(.available) | .sku' | head -1
BASE=https://api.ryustorefront.com bash scripts/manual-checkout.sh <sku>
```
Proof the order is paid (no database access yet): Stripe's event delivery log shows `checkout.session.completed` with HTTP 200,
and calling pay again for the same order answers `409 order_not_payable (paid)`.

## 4. End a session: destroy and audit

```
aws login --profile "$AWS_PROFILE"
scripts/aws-terraform.sh -chdir=infra/session plan -destroy -out=tfplan -var image_tag=unused -var web_image_tag=unused
```
Expect: **`Plan: 0 to add, 0 to change, 44 to destroy.`** (derived from the 44 applied; 31 observed 2026-11-15 before phase 6). Read it: nothing from `foundation` may appear.

```
scripts/aws-terraform.sh -chdir=infra/session apply tfplan
scripts/aws-terraform.sh -chdir=infra/session state list        # expect: no output
scripts/aws-audit.sh
```

`aws-audit.sh` runs 13 direct checks (RDS instances, load balancers, target groups, ECS clusters, tagged VPCs, NAT gateways,
unattached Elastic IPs, ACTIVE task definitions, `storefront-*` security groups, the log group, `storefront*` IAM roles, the `api`
A record, RDS-managed `rds!` secrets). Expect `ok` on every line, then the expected remainder: the zone, the ECR repository, the
two `storefront/` secrets and the certificate. The tag search printed last **lags behind deletions** and can still list the ECS
service, cluster or security-group rules for a while; trust the direct checks. Exit code 0 means clean.

Save the destroy output with the `Destroy complete! Resources: 44 destroyed.` line; it was not captured last time.

## 5. Rebuild from an empty account

Use this only for a new account or after section 6. Order matters; each step depends on the one before.

**5.1 State bucket** (not managed by Terraform; losing it loses the state history):

```
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
BUCKET=storefront-tfstate-$ACCOUNT
aws s3api create-bucket --bucket "$BUCKET" --region us-west-2 \
  --create-bucket-configuration LocationConstraint=us-west-2
aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled
```
Also before the first apply: the $20/$50 budgets and billing alerts (CLAUDE.md section 7.9).

**5.2 Route 53 zone, by hand** (Terraform imports it rather than creating it, so a plan can never silently replace it):

```
aws route53 create-hosted-zone --name api.ryustorefront.com --caller-reference "storefront-$(date +%s)" \
  --query 'DelegationSet.NameServers' --output text
```
Note the four name servers it prints.

**5.3 Delegate `api` in Cloudflare.** In the Cloudflare dashboard for `ryustorefront.com`, DNS: add four records, type `NS`, name
`api`, one per name server from 5.2 (remove any old `api` NS records first). Then check against Cloudflare's own name server, not
your local resolver (which may cache the old answer):

```
dig +short NS ryustorefront.com                                   # shows the two Cloudflare name servers
dig +norec NS api.ryustorefront.com @<one-of-those-cloudflare-ns> # AUTHORITY section: the four awsdns servers from 5.2
```
Observed 2026-11-19: the referral returned `ns-1157.awsdns-16.org`, `ns-1998.awsdns-57.co.uk`, `ns-28.awsdns-03.com`, `ns-858.awsdns-43.net`.
If `list-hosted-zones-by-name` ever shows two zones for `api.ryustorefront.com`, keep the one whose name servers match Cloudflare and delete the empty one.

**5.4 Foundation: import the zone, plan, apply.**

```
scripts/aws-terraform.sh -chdir=infra/foundation init -backend-config="bucket=$BUCKET"
ZONE_ID=$(aws route53 list-hosted-zones-by-name --dns-name api.ryustorefront.com \
  --query 'HostedZones[0].Id' --output text | sed 's|/hostedzone/||')
scripts/aws-terraform.sh -chdir=infra/foundation import aws_route53_zone.main "$ZONE_ID"
scripts/aws-terraform.sh -chdir=infra/foundation plan -out=tfplan
```
Expect: **`Plan: 7 to add, 1 to change, 0 to destroy.`** (derived; observed as 2 + 5 in two earlier applies). The 7 are the ECR repository,
its lifecycle policy, two secrets, the certificate, its validation record and the validation. The 1 change must be the zone's comment
and tags only: if it shows a name-server change or `must be replaced`, stop.

```
aws login --profile "$AWS_PROFILE"
scripts/aws-terraform.sh -chdir=infra/foundation apply tfplan
aws acm list-certificates --region us-west-2 \
  --query "CertificateSummaryList[?DomainName=='api.ryustorefront.com'].Status" --output text    # expect: ISSUED
```
Observed: the validation record takes about 31 s.

**5.5 Load the secrets** (values come from your git-ignored `.env` through stdin; they never print and never enter Terraform state):

```
set -a; . ./.env; set +a
printf %s "$STRIPE_SECRET_KEY" | aws secretsmanager put-secret-value --region us-west-2 \
  --secret-id storefront/stripe-secret-key --secret-string file:///dev/stdin
```
Use a **test-mode** key (`sk_test_`). For the webhook secret:

1. Stripe dashboard, test mode, Developers, Webhooks (event destinations): add an endpoint
   `https://api.ryustorefront.com/api/stripe/webhook` for the four events `checkout.session.completed`,
   `checkout.session.expired`, `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`.
   The endpoint can be created before the API exists; Stripe only needs the URL.
2. Copy its signing secret (`whsec_...`; not the one `stripe listen` prints) into `.env` as `STRIPE_WEBHOOK_SECRET`, then:
   ```
   set -a; . ./.env; set +a
   printf %s "$STRIPE_WEBHOOK_SECRET" | aws secretsmanager put-secret-value --region us-west-2 \
     --secret-id storefront/stripe-webhook-secret --secret-string file:///dev/stdin
   ```

Both persist, so this is done once, not per session. If you load a secret while a task is running, restart it so it reads the new value:
`aws ecs update-service --cluster storefront --service api --force-new-deployment`.

**5.6 Continue with section 3.**

## 6. Destroying the foundation (rare)

Only to leave the project or change accounts. Expect to redo section 5 afterwards.

1. Destroy the session first (section 4).
2. Keep the zone (so the Cloudflare delegation stays valid), or delete it on purpose:
   - keep it: `scripts/aws-terraform.sh -chdir=infra/foundation state rm aws_route53_zone.main`
   - delete it: remove `prevent_destroy` in `infra/foundation/main.tf`, and remove the `api` NS records in Cloudflare afterwards.
3. ECR refuses to delete a repository that holds images (`RepositoryNotEmptyException`). Delete them first:
   `aws ecr list-images --repository-name storefront --query 'imageIds[*]' --output json > ids.json` then
   `aws ecr batch-delete-image --repository-name storefront --image-ids file://ids.json`.
4. `plan -destroy -out=tfplan`, read, `apply tfplan`.
5. The two secrets go into a 30-day recovery window. Recreating them with Terraform within that window fails with
   "scheduled for deletion". Either `aws secretsmanager restore-secret --secret-id <name>` and `terraform import` it, or
   `aws secretsmanager delete-secret --secret-id <name> --force-delete-without-recovery` before the next apply.
6. Run `scripts/aws-audit.sh`; the "expected to remain" list should now be empty.

## 7. When something goes wrong

| Symptom | Cause and fix |
| --- | --- |
| `Could not export credentials for profile` | `aws login --profile ryustorefront` (with `--remote` on a headless VM). |
| Apply stopped with an auth error, `errored.tfstate` appears | The credentials expired midway. `docker ps` must show no Terraform container; `aws login`; if the state is locked, `scripts/aws-terraform.sh -chdir=infra/<stage> force-unlock -force <ID from the error>`; then `state push errored.tfstate`, plan again and read it before applying. Seen on 2026-11-23 during a destroy: the RDS deletion and the 5-minute internet gateway outlast a login made minutes before; run `aws login` immediately before `apply`. |
| Session plan fails on `data.aws_acm_certificate.api` or `data.aws_route53_zone.main` | The foundation is missing or the certificate is not `ISSUED`. Section 3.2 or 5.4. |
| Task keeps stopping with `ResourceInitializationError` | A secret has no value, or the execution role cannot read it. Section 3.3. |
| Target unhealthy, then the task is replaced | Startup longer than the grace period (180 s), or a crash. `aws logs tail /storefront/api --since 15m`. |
| A resource is fine in AWS but Terraform wants to replace it after a failed apply | `untaint` it instead of letting Terraform replace it (CLAUDE.md section 7.6). |
| The audit's tag search lists resources the direct checks call `ok` | Known lag in the tagging API. Wait and rerun; the direct checks are the source of truth. |

## 8. Open items (not done; each is a later session, not a claim)

- **Phase 6 is written, not run:** sections 3A and 3.5b/3.5c, the worker and web services, CloudFront photos and SES mail have
  not been applied or observed in AWS yet. Every count and expectation there is derived from the code.
- **SES production access** (mail to unverified addresses) is a request to AWS; out of scope for the demo.
- **Reading the database** without opening RDS (ECS Exec or a read endpoint).
- **Webhook replay** and out-of-order delivery against the cloud endpoint; refunds.
- **Real cost** per session and per idle month from Cost Explorer (tag `Project=storefront`; it lags about a day).
- Read-only root filesystem with a writable `/tmp`; memory use against `MaxRAMPercentage=70`; `sslmode=verify-full`.
- The old `ryustorefront.net` zone `Z0570260277C4LJ3ARO8T` is outside Terraform and still to be deleted by hand ($0.50 a month, list price).
