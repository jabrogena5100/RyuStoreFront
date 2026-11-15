# Decisions

Settled 2026-11-14 (phase 0). Versions were checked against Maven Central, Docker Hub and Spring Initializr on that date.

| # | Decision | Choice | Tradeoff accepted |
| --- | --- | --- | --- |
| 1 | Framework | Spring Boot 4.1.2, Java 25 (LTS) | Newer, fewer tutorials than 3.x; 3.5 was a worse starting point. Quarkus and Micronaut would be a distraction from the shop itself. |
| 2 | Build tool | Maven with committed wrapper (`./mvnw`) | Slower than Gradle; declarative and common in job posts, easy Docker caching. |
| 3 | Worker packaging | One jar, `api` and `worker` profiles, two ECS services | Looser dependency graph than separate modules; one build and one image. Split only if it hurts. |
| 4 | Stock reservation | Atomic `UPDATE ... WHERE stock >= n` plus a reservation row with expiry | Needs a worker to release expired holds; no row locks, easy to test concurrently. `CHECK (stock >= 0)` is the backstop. |
| 5 | Auth | Server-side sessions (Spring Session JDBC, HttpOnly cookie) | Needs CSRF protection and cookie forwarding from Next.js; same-domain routing (`/api/*` on the ALB) avoids CORS. |
| 6 | Local stand-ins | Mailpit for email; SQS/S3 emulator chosen in phase 4 (LocalStack, or ElasticMQ + MinIO if licensing blocks it). Guest cart is a DB row keyed by a signed cookie. | Emulators differ from AWS in details; real AWS is exercised in phases 4b and 7. |

Out of scope (deliberate): real payments, Stripe Connect and Tax, multi-vendor, multi-currency, ML recommendations, mobile app, microservices.

Pinned: Postgres 18.7 (local; check RDS support before phase 4b), Spring Modulith 2.1.x, Testcontainers 2.0.5, Terraform 1.17.2 (Docker wrapper), Temurin JDK 25.

## Slice 3b (authentication), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Password hashing | `DelegatingPasswordEncoder`, BCrypt (`{bcrypt}` prefix stored) | Argon2 is stronger against GPUs but needs Bouncy Castle; the prefix lets the algorithm change later. BCrypt limits passwords to 72 bytes, enforced in validation. |
| Login mechanics | JSON controller calling `AuthenticationManager`, session id changed and CSRF token rotated on sign-in | More code than `formLogin`, but JSON responses and no redirects for the Next.js client. |
| CSRF | Token kept in the session, delivered by `GET /api/auth/csrf`, required on every POST including register and login | Client must fetch a token first; this prevents login CSRF. Plain (not XOR-masked) token handler, since the token only travels in JSON. |
| Errors | One body `{code, message, fields}` for the whole auth API; JSON 401 and 403, no redirects | Codes are a contract the design depends on (`docs/api/auth.md`). |
| Email | Trimmed, lowercased, printable ASCII, at most 100 characters | Rejects internationalised addresses; the limit comes from Spring Session's `PRINCIPAL_NAME VARCHAR(100)`. |
| Admin accounts | Never created by registration (role is always `customer`); promoted by SQL or seed | No admin UI for user management in v1. |

## Slice 3c (cart), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Guest cart identity | Random 256-bit token in the `CART` cookie; only its SHA-256 is stored (`carts.guest_token_hash`) | Replaces the "signed cookie" idea in the old plan: no signing key to manage or rotate, nothing to forge, a database leak yields no usable cookies. Orphaned guest carts need a cleanup job (worker, phase 4). |
| Line addressing | By SKU (what the catalogue API exposes), quantity is set not incremented | Idempotent retries; a client must know the SKU, which the product page already has. |
| Quantity cap | 10 per line, enforced by the API (400 `max`) and by `CHECK (quantity BETWEEN 1 AND 10)` | Never silently clamps. |
| Stock | The cart never reserves stock; `PUT` rejects quantities above current stock (409) and lines are flagged when stock later falls | Another buyer can take the last pair; reservation happens at checkout (3d). |
| Merge on sign-in | `auth` publishes a `SignedInEvent`; `cart` listens, merges in one transaction under a row lock, larger quantity wins, then retires the cookie | The event carries the servlet request/response (listeners need the cookie). A failed merge is logged and never blocks login; it retries at the next sign-in. Dependency is cart to auth only, checked by Modulith. |
| Error contract | `ApiError`/`ApiException` and one handler moved to `shared` (marker `@StructuredErrors`); auth and cart use it | Small refactor of slice 3b; the auth tests assert the same codes. |

## Slice 3d (checkout start), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Who can buy | Signed-in customers only; guests may fill a cart (merged at sign-in) | Fewer abandoned-account surprises and one owner for every order; costs a sign-in step before payment. |
| Atomicity | One transaction: lock, replay check, supersede, snapshot order, reserve stock. `StockReservations` joins the caller's transaction (`@Transactional` REQUIRED) | A failure anywhere leaves no order and no hold. Stock row locks are held until commit (a short time). |
| Idempotency | Required `Idempotency-Key` header, unique per customer (V8 partial unique index), replay returns the stored order | A replay with a different body is not compared; documented. |
| Concurrency per customer | `pg_advisory_xact_lock(customerId)` at the start | One customer's checkouts serialise; it also removes the unique-violation race on a duplicate key. |
| Abandoned attempts | A new checkout cancels the customer's other pending orders and releases their stock before validating (rolled back if the new attempt fails) | A minimal `cancelPending` lives in `orders` until phase 4's state machine replaces it. |
| Cart after checkout | Kept until payment succeeds (phase 4) | Cart and order can briefly show the same items. |
| Reservation TTL, shipping | `storefront.checkout.reservation-ttl-minutes` (30), `storefront.checkout.shipping-flat-minor` (800) | Flat rate only; no tax engine. |
| Order expiry | `orders.expires_at` (V8) holds the reservation expiry, indexed for pending orders | The worker can find overdue pending orders without joining reservations. |

## Phase 4, slice 1 (order state machine), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Where the rules live | One table in the `OrderStatus` enum; every change goes through `Orders.transition` (see `docs/order-lifecycle.md`) | The database has only the CHECK on valid values; the legal moves are application code, tested in full (all 36 pairs). |
| Concurrency | `SELECT ... FOR UPDATE` on the order row inside `transition` | Pessimistic and simple; concurrent webhooks for one order serialise. The `version` column is still incremented for any future optimistic use. |
| Results, not exceptions | `TransitionResult` (`APPLIED`, `ALREADY`, `ILLEGAL`, `NOT_FOUND`) | Callers must check the outcome; avoids poisoning the caller's transaction. |
| Lock order | order row, then reservations, then variants, application-wide | Checkout's supersede step was reordered to cancel before releasing stock (fixes a possible deadlock with a webhook that pays the same order). |
| Queue (phase 4 decision) | The worker drains the Postgres outbox directly (`FOR UPDATE SKIP LOCKED`), behind a small publisher interface so SQS can be added later | Matches the plan's "SQS in AWS" only if added later; no emulator needed locally. |
| Design phase D | Deferred by the owner until after the backend phases; phase 5 (frontend) cannot start without it | The API contracts in `docs/api/` are the input to design when it happens. |

## Phase 4, slice 2 (outbox and worker), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Where events come from | `Outbox.publish` is `MANDATORY`-transactional; `Orders.transition` publishes `order.<status>` in its own transaction | An event can never exist without its change, or vice versa. Callers cannot publish "fire and forget". |
| Delivery | Claim, handle and mark processed in one transaction, one event at a time, `FOR UPDATE SKIP LOCKED` | At-least-once: handlers must be idempotent. A kill redelivers instead of losing. Throughput is one event per transaction (fine for a shop; not a stream). |
| Failures | Rollback, then record in a separate step; backoff 5 s doubling to 15 min; dead after 10 attempts | A killed process is not counted as an attempt (see `docs/worker.md`). |
| No handler | Mark processed with a trace in `last_error` | A misconfigured deployment could drop events silently; the trace is visible in the table. |
| Packaging | `worker` profile sets `storefront.worker.enabled`; jobs are `@ConditionalOnProperty`; the worker still serves HTTP | Health checks keep working; the API image and the worker image are the same. |
| Sweep order | Cancel overdue orders first (order row, then reservations), only then release stray expired reservations | A reservation is never released while its order could still be paid. |
| JSON | Flat string payloads written by a small `Json` helper, read back with `jsonb_each_text` | No JSON library coupling; payloads are flat maps. Nested payloads would need a library. |

## Phase 4, slice 3 (email), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Local mail | Mailpit `v1.33.1` in `compose.yaml` and as a Testcontainers container in the email tests | One more container in the test run; the tests read the real inbox through Mailpit's API. |
| Double sends | A `sent_emails` ledger (V9) keyed per email; written in the worker transaction before the send | A crash between the server accepting the mail and the commit sends a duplicate rather than losing the email. |
| Content | Pure `OrderConfirmationEmail`, multipart text and HTML, everything dynamic HTML-escaped | Hand-written templates; fine for one email, a template engine only if there are many. |
| Health | `management.health.mail.enabled=false` | A mail outage is invisible to the load balancer by design; it will need its own alarm (pending-event age, phase 6). |
| Order summary | `OrderSummary` now carries the shipping address (also in the checkout response) | The checkout JSON grew one object; documented in `docs/api/checkout.md`. |

## Phase 4, slice 4a (starting payment), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Provider integration | Stripe Checkout (hosted page), official `stripe-java` 34.1.0 (newest stable on Maven Central; the "latest" tag there is a beta), behind a `PaymentGateway` interface | A fake gateway in tests means the real Stripe call is only proven by a manual run with a test key. |
| Hold vs payment page | Creating the page extends the order and reservation hold to page expiry + 5 min. Page lifetime 31 min (Stripe's minimum is 30 from creation, per its docs) | The hold can exceed the original 30 minutes by up to about 11 minutes. |
| One page per order | A second page after the first expired is refused (`payment_expired`) | A shopper who is too slow starts a new checkout; in exchange stock cannot be held indefinitely. |
| Concurrency | The order row is locked for the whole pay call, including the Stripe request | One slow Stripe call blocks that one order; two clicks create one session. No Stripe idempotency key: params (expiry) differ per attempt, so a key would fail on retry. |
| Ownership | A customer's id is part of the lookup; someone else's order is "not found" | Indistinguishable from a missing order by design. |
| Return URL | `STOREFRONT_PUBLIC_BASE_URL/orders/{id}/confirmation?session_id=...`; arriving there proves nothing | The order becomes `paid` only through the webhook (4.4b). |

## Phase 4, slice 4b (the Stripe webhook), settled 2026-11-14

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Signature check | `Webhook.Signature.verifyHeader` (SDK) on the raw request bytes (`byte[]` body), 5 minute tolerance, header missing/garbled/stale is 400, no secret configured is 503 | The tolerance window allows replay of a captured request for 5 minutes; that is harmless because event ids make a replay a no-op. |
| Event parsing | Gson (declared in the pom: stripe-java only brings it at runtime scope) over the raw JSON, a handful of fields; not the SDK's typed `Event` | A renamed field in a future API version reads as "unknown order" (200) instead of failing; tests use an event with a made-up future `api_version` to keep the code independent of it. |
| Idempotency | `INSERT stripe_events ... ON CONFLICT DO NOTHING` first, one transaction per event, any exception rolls back the event id too | Concurrent duplicates wait on the primary key (one connection each); the same payment under two event ids is made safe separately by the order lock and `payments` unique constraints. |
| Paying | Totals checked first, then `transition(PAID)`, then payment row, then `StockReservations.commit` (order lock first, as everywhere) | A paid session without `payment_intent` throws (500) rather than being guessed at. |
| Money we cannot honour | Record the `payments` row, queue `payment.refund_requested` through the outbox, refund with a Stripe idempotency key. Late payment: order stays `cancelled`. Hold gone: `paid` to `refunded`. Amount/currency mismatch: order cancelled and stock released | Refund happens after commit, so there is a short window (worker poll interval) where the shopper is charged and not yet refunded. A mismatch on an already-paid order is logged for a human, not refunded. |
| `order.paid` for a refunded order | The event is still published (it comes from `transition`); the e-mail and cart handlers skip orders that are `refunded` or `cancelled` | Handlers read the order's current status; a legitimately paid order refunded by an admin before the worker runs would also get no e-mail. Accepted: it is not "confirmed" any more. |
| Cancelling on expiry | Only when the event's session id equals the order's stored session id | An event for any other session is ignored (logged), even if its metadata names the order. |
| Cart emptied | A second `order.paid` handler in `checkout` deletes the purchased variants' lines from the customer's cart | Runs when the worker runs, not inside the webhook request; a few seconds of delay. If a later handler (the e-mail, ordered last) fails, the cart deletion rolls back and is redone on retry. |
| Outbox handlers | Several handlers per event type, run in `@Order` inside the claim transaction | All-or-nothing per event: one failing handler retries the others too, so every handler must be idempotent (they are). |

## Phase 4b (cloud infrastructure layout), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Domain | Registered at Squarespace, delegated to a Route 53 hosted zone (Route 53 registration failed with no reason given) | Name servers are pasted by hand at the registrar; no transfer, which a new registration could not do for 60 days anyway. |
| Stages | `infra/foundation` (zone, ECR; kept) and a later per-session stage (VPC, RDS, ECS, ALB, ACM, secrets; destroyed after each session) | Two states to manage; the destroy can never touch DNS or the registry. |
| Zone adoption | The hand-made zone is imported, with `prevent_destroy` | A second zone would have different name servers and silently break the delegation. |
| State | S3 bucket created by CLI (versioned, public access blocked), native lockfile (`use_lockfile`), no DynamoDB | The bucket itself is not in Terraform; losing it loses the state history. |
| ECR | Immutable tags, scan on push, keep the last 15 images | A tag cannot be re-pushed; each build needs a new tag (commit SHA). |

## Phase 4b (per-session stage), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Task networking | Fargate tasks in public subnets with public IPs, no NAT gateway; security groups allow inbound only from the ALB, RDS only from the tasks | Saves the NAT gateway's hourly charge (not measured; verify in Cost Explorer) and one resource to destroy. Tasks are reachable at the IP level in principle, so the security group is the only barrier; say so in the write-up rather than call it a private network. |
| API hostname | `api.ryustorefront.net` (Route 53 alias to the ALB, ACM certificate for that name) | Earlier plan (slice 3b) was same-domain `/api/*` routing to avoid CORS. A subdomain is the same site (SameSite=lax cookies still work) but a different origin, so the frontend must either proxy `/api` through Next.js server-side or the API must send CORS headers with credentials. Settle that before phase 5; phase 4b needs only the API. |
| RDS engine | PostgreSQL 18.6 on `db.t4g.micro`, single-AZ, pinned exactly with automatic minor upgrades off | Local and Testcontainers run 18.7, which RDS did not offer for this class on 2026-11-15; one patch version of difference, not tested against RDS yet. |

## Phase 4b (API slice), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Where Stripe secrets live | Empty Secrets Manager shells in the persistent foundation stage; values loaded by the owner from `.env`, never through Terraform | About $0.40 per secret per month (list price, unverified) instead of reloading each session; the values do not appear in Terraform state. |
| Certificate | ACM certificate and its DNS validation records in the foundation stage | Free and no per-session validation wait; the cert outlives a destroyed session. |
| Services in this slice | API only; the worker (emails, cart clearing, reservation expiry) comes later | A paid order will not clear the cart or send mail in this slice; outbox rows accumulate. Matches phase 4b in CLAUDE.md ("API alone with one real checkout"). |
| Task start gating | `desired_count` is 0 until the secrets hold values | One extra apply; avoids a crash loop on a missing secret. |
| Read-only root | Off for the first run (`read_only_root = false`) | CLAUDE.md section 5 wants it on; a writable `/tmp` volume owned by a non-root user is the unknown, so it is tried after the first success. |
| Demo data | `seed` profile, 100 products, only when the database is empty | The cloud API starts with a profile that is not "production"; acceptable for a demo, noted for the write-up. |
| Database TLS | `sslmode=require` | Encrypts but does not verify the server certificate; `verify-full` needs the RDS CA bundle in the image (later hardening). |
