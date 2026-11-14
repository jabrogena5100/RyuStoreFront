# E-commerce store: project plan (Java backend, designed UI)

Working name: **Storefront** (rename freely). A portfolio project: a real, deployable online store whose hard parts are the ones
interviewers ask about: **payments, inventory correctness, order lifecycle, async processing, and operating it in the cloud.**
Backend in **Java (Spring Boot)**; UI designed first in **Claude Design** so it does not look generated.
Everything here is a plan, not a record. Nothing is built or verified yet.

## 1. What makes it resume-worthy (and what does not)

Plenty of candidates have "product list + cart". What stands out is correctness under failure, shown with evidence:

| Topic | What you build | The proof you keep |
| --- | --- | --- |
| Payments | Stripe Checkout in **test mode**; webhook endpoint with signature verification; idempotent handling (Stripe retries and re-orders events) | Test that replays the same webhook 3 times and out of order: one order, one payment record |
| Inventory | Stock reservation at checkout with expiry; no overselling under concurrency | A concurrency test (e.g. 50 buyers, 10 units: exactly 10 orders) and a k6 run you can quote |
| Order lifecycle | Explicit state machine: `pending -> paid -> fulfilled -> shipped`, plus `cancelled` and `refunded`; illegal transitions rejected | Table-driven tests of every transition |
| Async work | Transactional outbox + worker: emails, stock release on expiry, order events (SQS in AWS, a local queue in Compose) | Kill the worker mid-job; the job completes after restart |
| Search and catalog | Postgres full-text search with filters, pagination, price sort; product images in S3 behind CloudFront | Query plans (`EXPLAIN`) for the main queries, with indexes you chose |
| Ops | Terraform, CI, staged deploy, alarms, a destroy-and-audit routine | A deployment log and verification doc like RyuCloud's |
| Design | A design system and key screens made before coding, implemented from tokens | Design files in the repo, Lighthouse/axe results, screenshots of design vs build |

**Avoid claiming** "scalable", "high-traffic" or "production-ready" without measured numbers. Quote only what a test or load run showed, with the setup.

## 2. Scope

**In (v1):** catalog with categories and variants (size/colour), search, product detail, cart (guest and signed-in), checkout through Stripe (test mode),
order history, order confirmation email, admin area (products, stock, orders, refunds), seeded demo data, health/readiness, structured logs.

**Out (say so in the README, it reads as judgement, not a gap):** real payments, tax/shipping rate engines (use flat rate), multi-currency,
marketplace/multi-vendor, recommendations/ML, mobile app, microservices.

**Pick one niche** for the demo data (for example keyboards, plants, or coffee). A coherent catalogue makes screenshots and demos feel real.

## 3. Architecture and stack

Same shape as RyuCloud so the operating skills transfer. **New** marks things you have not done yet.

- **API:** Java (current LTS; check the version at project start and pin it) with **Spring Boot** (Web, Validation, Security, Actuator). Build with a committed wrapper (`./mvnw` or `./gradlew`) so the VM needs only a JDK.
  Modular monolith by packages/modules: `catalog`, `cart`, `checkout`, `orders`, `inventory`, `payments`, `auth`, `admin`. **New:** enforce the boundaries with **Spring Modulith** (or ArchUnit) so "modular monolith" is checked by a test, not a claim.
- **Worker:** the same codebase run with a `worker` profile (one image, one build) or a second module: decided in section 3.1. It drains the **outbox** (events written in the same DB transaction as the order, then published), sends email, releases expired reservations.
- **DB:** PostgreSQL, migrations with **Flyway** (the goose equivalent). Money as integer minor units plus currency in a small value type, never `double`. Foreign keys and `CHECK (stock >= 0)` constraints do real work.
- **Persistence:** **Spring Data JPA** for the order/cart aggregates (a common interview topic: show you handle N+1, lazy loading and transaction boundaries), and **plain SQL** (`JdbcClient`) for the two hot paths: the atomic stock update and search.
- **Queue:** SQS in AWS; locally a Compose service (LocalStack or a Redis-backed queue, decided in 3.1). No Redis in AWS (keeps cost and moving parts down); the cart lives in Postgres.
- **Frontend:** Next.js + TypeScript, server-rendered product pages (SEO and Lighthouse numbers you can quote). Built from the design tokens (section 6).
- **Payments:** Stripe test mode with hosted Checkout (no card data touches your servers; say so in the security notes). Official Stripe Java SDK; signature check on the raw request body.
- **Email:** SES sandbox or a local mail catcher in Compose.
- **Images:** S3 + CloudFront with an origin access control; admin uploads by presigned URL.
- **Tests:** JUnit 5, **Testcontainers** (real Postgres, LocalStack for S3/SQS), Awaitility for async assertions, a real concurrency test for stock, Playwright for end-to-end, k6 for load.
- **Infra:** Terraform (pinned, through the Docker wrapper), ECS Fargate, RDS (single-AZ), ALB, ACM, Route 53, Secrets Manager, CloudWatch, GitHub Actions. Temporary sessions, destroyed afterwards.
- **Observability (new):** request IDs in logs, Micrometer metrics for business events (orders/minute, payment failures, reservation expiries) and alarms on them; OpenTelemetry tracing only if time allows.

### 3.1 Decisions to settle in session 1 (one-paragraph tradeoff each, before any code)

1. **Spring Boot vs a lighter framework** (Quarkus, Micronaut). Recommendation: Spring Boot (most common in job posts; the point is the shop, not the framework).
2. **Maven vs Gradle.** Recommendation: whichever you want to learn; both are fine. Commit the wrapper.
3. **Worker packaging:** one jar with profiles (simplest, one image) vs a separate module (cleaner dependency graph). Recommendation: one jar, two ECS services with different profiles, until it hurts.
4. **Reservation strategy:** `SELECT ... FOR UPDATE` row locks vs an atomic `UPDATE ... WHERE stock >= n`. Recommendation: the atomic update; simpler and easy to test.
5. **Auth:** server-side sessions (Spring Session JDBC, HttpOnly cookie) vs stateless JWT. Recommendation: sessions; fewer ways to get it wrong, no Redis needed.
6. **Local queue and email stand-ins** (LocalStack/Mailpit vs alternatives), and the guest-cart storage (DB row keyed by a signed cookie is the recommendation).

## 4. Phases (each has an exit test; stop at a failed test)

| # | Phase | Exit test |
| --- | --- | --- |
| 0 | New VM, JDK and tooling, repo, CI skeleton, first `CLAUDE.md`, decisions in 3.1 | Compose starts Postgres; `./mvnw verify` (or Gradle) is green in CI; ruleset on `main` |
| 1 | Schema and domain: products, variants, stock, carts, orders, order items, payments, outbox. Money type. Flyway migrations | Migrations apply on a fresh DB and in Testcontainers; constraint tests pass |
| D | **Design** (in Claude Design, section 6): design system and key screens, approved by you | Tokens file and screen exports committed under `design/`; you sign off each screen |
| 2 | Catalog API + search + seed | `EXPLAIN` shows indexes used; 1,000 seeded products; pagination tests |
| 3 | Auth, cart, checkout, reservation | Concurrency test: exactly N successful reservations for N units |
| 4 | Stripe Checkout, webhook, idempotency, order state machine, outbox + worker, email | Webhook replay/out-of-order test; worker-kill test |
| 4b | **Thin cloud slice** (temporary session): deploy the API alone with a health endpoint and one real checkout | Verification rows from real output; destroy plus audit clean |
| 5 | Frontend from the approved design: catalog, product, cart, checkout, orders, admin | Playwright e2e of the whole purchase against Compose; design-vs-build screenshots |
| 6 | Hardening: rate limits, validation, security headers, dependency scan, k6 load run, backup/restore note, accessibility pass | Written results with numbers and the setup |
| 7 | Full AWS session: staged apply, images, migrate, real purchase in the cloud, failure drills | `verification.md` rows from real output; destroy plus audit clean |
| 8 | Portfolio write-up: README with architecture diagram, demo video, screenshots, decisions, "what I would do next" | A stranger can read the README and run it locally in 10 minutes |

Rough effort at a few evenings a week: phases 0-1 about 2 weeks, D about 1 week, 2-4 about 4 weeks, 5 about 2 weeks, 6-8 about 2 weeks. Cut phase 6 items before cutting tests.

## 5. Java-specific things to plan for

- **Memory and cold start:** a Spring Boot task needs more memory than a Go binary. Start the API task at about 0.5 vCPU / 1 GB and measure; the health-check grace period on the ECS service must cover startup (tens of seconds). This raises the per-session cost a little: **not measured, verify with Cost Explorer.**
- **Read-only root filesystem:** RyuCloud's API ran with a read-only root; Tomcat and the JVM write to `/tmp`. Give the task a writable `/tmp` volume (or drop the read-only setting) and check the first start's logs. Unverified until run.
- **Image builds:** Maven/Gradle downloads many dependencies, which hurt on this VM's slow default Docker bridge. Use `docker build --network host`, BuildKit cache mounts for the dependency cache, and a layered or multi-stage Dockerfile (JRE runtime image, non-root user). Jib (builds without the Docker daemon) is an alternative worth considering.
- **JVM flags in containers:** set the heap as a percentage of the container limit (`-XX:MaxRAMPercentage`), and measure; do not guess.
- **Transactions:** keep `@Transactional` boundaries small and explicit around the outbox write and the stock update; test that a rollback leaves no outbox row.
- **Virtual threads:** optional; adopt only if a measurement shows a benefit, and say so in the write-up.

## 6. Design with Claude Design (to avoid generic-looking UI)

Design before the frontend (phase D), in claude.ai with Claude Design. The goal is one coherent system, not a generated template.

1. **Brief first** (half a page, in the repo as `design/BRIEF.md`): the niche, the audience, three adjectives for the brand, two or three reference sites you admire and *what* you like about each, and what to avoid.
2. **Design system before screens:** colour tokens (a restrained palette: one accent, neutrals, semantic colours), type scale (one or two typefaces), spacing scale, radii, elevation, motion rules, and the core components (button, input, card, price, badge, nav, toast, empty state, error state).
3. **Key screens**, each in desktop and mobile: home, listing with filters, product detail, cart, checkout, order confirmation, order history, admin product edit, admin orders. Include **empty, loading and error states**: those are what generated designs usually skip.
4. **Anti-slop checklist** (review every screen against it): no lorem ipsum or "Welcome to our store" copy; real product names, prices and descriptions from your seed data; no stock-gradient hero with a centred tagline; one consistent icon set (no emoji as icons); consistent spacing from the scale, not ad hoc; real, consistent product imagery (shoot or source photos with licences; do not mix styles); sufficient contrast (WCAG AA), visible focus states, tap targets 44 px; copy written for the product, not generic.
5. **Hand-off into code:** export tokens to one file (CSS variables or Tailwind config) that is the single source of truth; components in the app use only tokens. Commit exports under `design/` with a short `design/README.md` explaining decisions.
6. **Verify the build against the design:** Playwright screenshots of key pages next to the design exports; Lighthouse accessibility and performance; an axe scan in CI. Record the numbers you actually got.

Note: I (Claude Code) cannot see how Claude Design behaves on your account beyond what you paste or export. Bring the exports back into the repo and the new session will implement from them.

## 7. Lessons from RyuCloud to apply on day one

These all cost real time last time; each is cheap to avoid.

1. **Test what the handler builds, not only what the helper accepts.** The signed-cookie bug (`*` escaped to `%2A`) passed unit tests that fed the signer a hand-written string.
   Assert on the real output of the real code path (cookies, URLs, webhook payloads, serialized JSON).
2. **Run the cloud deployment early** (phase 4b), not at the end. Most RyuCloud surprises were cloud-only.
3. **Terraform in Docker uses fixed credentials that expire in about 15 minutes** (`aws login`). Apply in stages (certificates, CloudFront, rest) and destroy in stages. Keep the `errored.tfstate` recovery steps from the old runbook handy.
4. **Domains:** registering through Route 53 auto-creates a second hosted zone. Point the registrar's name servers at the zone Terraform fills, and verify with `dig +norec NS <domain> @<tld-server>`, not your local resolver.
5. **Docker builds on that VM are slow on the default bridge:** use `docker build --network host` (more important with Java dependencies).
6. **Save a plan to a file and read it before every apply**; untaint a resource that is fine in AWS instead of letting Terraform replace it.
7. **Audit script:** the tag search lags after deletions; trust the direct service checks and make the script say so.
8. **Keep an evidence log from the first session** (`verification.md`, `deployment-log.md`): claims marked *run*, *user-reported* or *not done*.
9. Create the **$20/$50 budgets and billing alerts before** the first apply.

## 8. New VM setup checklist (phase 0)

Ubuntu LTS VM, then (you run these; ask Claude Code for the exact commands for your versions):
- Docker Engine + Compose plugin; your user in the `docker` group.
- A JDK of the pinned LTS version (for local runs outside Docker). The Maven/Gradle wrapper downloads the build tool itself.
- Node.js LTS for the frontend (or run it only through Docker, as in RyuCloud).
- AWS CLI v2 (needs `aws login`, 2.32+); an IAM user with MFA, no access keys. Decide: new AWS account or the same one (use a different resource prefix and state key).
- Terraform pinned through the Docker wrapper script (copy `scripts/aws-terraform.sh`, `aws-audit.sh`, `validate-terraform.sh` from RyuCloud and adapt the prefix).
- `gh` CLI, an SSH key added to GitHub, `jq`, `openssl`, `k6` later, the Stripe CLI for local webhook testing.
- Git identity set by you; branch ruleset and secret scanning on from the first push.
- A new domain (cheap TLD) or a subdomain of the existing one (cheaper and quicker: delegate `shop.<domain>` to a new zone).

## 9. Costs (rough, list prices, unmeasured)

Same profile as RyuCloud (about $0.07/hour with services off, about $0.10-0.15/hour running, so $1-4 per session), plus a little more for larger Java tasks
(not measured). Fixed: domain and zone. Stripe test mode and the SES sandbox are free. Verify prices before you rely on them.

## 10. Resume bullets (write them only after the evidence exists)

Fill the numbers from your own `verification.md`; delete any bullet you cannot back.
- Built a Java/Spring Boot and PostgreSQL storefront with Stripe webhooks handled idempotently (replayed and out-of-order events tested) and an order state machine with every transition tested.
- Prevented overselling with atomic stock reservation; verified under **N** concurrent buyers in **M** runs using Testcontainers.
- Transactional outbox + worker for email and reservation expiry; verified by killing the worker mid-job.
- Enforced module boundaries with Spring Modulith; integration tests run against real Postgres.
- Designed the UI as a token-based design system before implementation; Lighthouse accessibility **A**, performance **P** on the product page.
- Defined all infrastructure in Terraform (ECS Fargate, RDS, CloudFront, SQS); deployed and torn down per session with a residual-resource audit; **$X** per session measured.
- Load-tested checkout with k6: **P95 Y ms at Z requests/s** on a single small task (setup documented).

## 11. First prompt for the new session

> Read CLAUDE.md and PLAN.md. We are starting phase 0. Inspect the (empty) repository, then propose the repository layout, the CI skeleton and the
> Compose file. Settle the decisions in PLAN.md section 3.1 first, with tradeoffs, and verify the current Java and Spring Boot versions before pinning them.
> Follow the handoff format; I run every command myself.
