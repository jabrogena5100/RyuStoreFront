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

## Phase D (design in code), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| How the UI is designed | In code, in `web/`, reviewed through Playwright screenshots; not in Claude Design (a deviation from CLAUDE.md section 6) | No drag-and-drop canvas and no second tool's opinion; the risk is a generic look, managed by the brief's checklist, real catalogue data on every screen and the owner's review. The tokens and components exist once, in the app. |
| Buyer and brand | People who want quick access to the footwear market across white shoes, streetwear, carbon-plate runners, evening shoes and women's boots; "quick, restrained, confident"; avoid cluttered, formulaic or loud layouts | Browse is by occasion and a one-screen quick finder, not by brand. |
| Colour | Near-black warm neutrals with depth from lightness steps, off-white text, one cobalt accent (hue 258); every value computed for WCAG contrast; dark only for v1 | Two first-draft values failed AA and were corrected; a light theme is not built. |
| Tokens | One CSS file; Tailwind's default palette is switched off so a colour outside the tokens cannot be used | Less convenient than the default palette; intended. |
| Placeholders | Typographic: an oversized tone-on-tone initial per product, one 4:5 frame; the shoe line drawings were tried and dropped | They say nothing about the product; real photos replace them at the same ratio. |
| Components | Native `<dialog>`, `<details>`, radio inputs and `<select>` before any component library | Less polish than a library for menus and selects; fewer dependencies and native accessibility. |
| Frontend versions | Node 26.13.1 LTS in Docker; Next.js 16.4.6, React 19.3.1, Tailwind 4.3.5, Motion 14.14.0, TypeScript 7.0.2 (works with Next 16.4.6 here), pinned exactly | TypeScript 7 is new; revisit if a tool breaks. |

## Phase D, layout and motion revision, settled 2026-11-15

The owner reviewed the first screens on GitHub and asked for a familiar shop layout and minimal motion.

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Layout | Two-tier header, equal category tiles, product rows, value strip, four-column footer; listing with a filter sidebar, phone drawer, active-filter pills and sort at top right (`design/BRIEF.md` 8a) | Reverses the brief's "no formulaic template rhythm" rule at the owner's request; the risk of a generic look is now carried by consistency, real data and the checklist. |
| Filters | Every option is a link to a URL with a count; the same server-rendered panel is the sidebar and the drawer content (native `<dialog>`) | Each click is a navigation, not an instant client update; in return no filter state to keep in sync, shareable URLs, and it works without scripts. Size chips carry their count in the accessible name, not on screen. |
| Counts | `countWith`: the pairs a filter would show given the other filters | Computed on the fixtures now; with the real API this becomes facet counts from the search endpoint (not built yet). |
| Price filter | Three bands (under $150, $150 to $300, over $300), `price` query parameter, in the fixtures only | The backend search already filters by `minPrice` and `maxPrice` (minor units, cheapest variant): a band maps to one or both, "under $150" being `maxPrice=14999`. What it lacks is facet counts (corrected 2026-11-15: an earlier version of this row said the price filter was missing). |
| Header | Not sticky; no menu dialog (the category bar scrolls sideways on phones) | A tall header scrolls away; cart is not reachable without scrolling up. A sticky header was left out to avoid offset bugs with sticky sidebars. |
| Motion | Colour and opacity transitions of 100 to 200 ms with one easing; no scroll-reveal, shared-layout, exit or hover-zoom animations, no shimmer; toast is a CSS fade-in; the `motion` dependency was removed | Less polish on state changes (the gallery image, size selection and the cart line removal now switch instantly). |
| Value strip and footer copy | Only statements the product can back: flat shipping from the API config, Stripe test mode, the size picker showing sold-out sizes. No popularity, ratings or delivery-time claims | "Live stock" is true once the page calls the API; today the data are fixtures, and the strip says "which sizes are still available", not a number. |

### Components taken from 21st.dev (through its MCP server, 2026-11-15)

Only generic search phrases were sent to the service (for example "product listing filter sidebar with checkbox groups and counts"); no project names, URLs, secrets or e-mail address. Two components were retrieved in full; none of their code was copied into the repository.

| Where it came from | What we used | Licence | What we did |
| --- | --- | --- | --- |
| "Ecommerce Category Page" (`ecommerce-04` by mohammadshehadeh, 21st.dev) | The arrangement only: sticky-sidebar grouped options with counts at the right edge, removable filter chips, "Show N results" in a phone sheet | The file header states MIT (copyright holder named there) | Rewritten as `ListingFilters.tsx`, `FilterDrawer.tsx` and the pills in `products/page.tsx` on native links and `<dialog>`; none of its Radix, shadcn or default-palette classes were kept, so there is no code to attribute beyond the idea. |
| "Footer with Navigation Grid" (`footer-01` by shadcnui-blocks, 21st.dev) | The arrangement only: link columns, a rule, a bottom line | The retrieved result carried no licence text and no licence field | Not copied. `Footer.tsx` is our own and tiny. Treat the licence as unconfirmed. |

The product row (scroll-snap with arrow buttons) was written from scratch; the carousels offered use animation libraries that the motion rule excludes.

## Phase D, checkout, orders and admin screens, settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Scope | Sign in, create account, checkout, pay step, confirmation, order history and detail, admin orders (list, detail with refund), admin products (list, edit) | The signed-in header menu is not designed; the header has a static "Sign in" link. |
| Contracts | Shopper screens follow `docs/api/{auth,checkout,payment}.md` state by state (field codes, `cart_changed`, `order_expired`, `payment_expired`, "confirming" until `paid`). Order history, order detail, admin and per-variant stock do not exist in the API: listed in `docs/api/proposed-ui-needs.md` | The admin screens and history are shaped by the design, not by an agreed contract; wiring may change them. |
| Admin chrome | Route groups: `(shop)` has the shop header and footer, `(admin)` has its own bar and navigation (sidebar, tabs on phones); same tokens and components | A route-group move of existing pages (no URL changes). |
| Lifecycle in the UI | Admin actions come from the rules in `docs/order-lifecycle.md` (`NEXT_STATUS`); illegal moves are not offered at all, refund asks first in a native `<dialog>` | The rules exist twice (backend and this table) until the API returns the allowed moves. |
| Pay step | Shows the hold from `expiresAt` and states for `payments_unavailable`, `payment_provider_error` and an expired order | The hold timer reads the clock after load; review screens pass a fixed clock so screenshots are stable. Dates and times are shown in UTC. |
| Review states | Every state is a `?state=` URL, as on the earlier screens; "Place order" goes to the pay step with a fixture order | Those URLs and the fixture navigation go away when the screens are wired. |
| Tables | Native `<table>` with captions and row headers on wide screens, a card list on phones | Two markups per list. |
| 21st.dev | Searched once earlier for the filter and footer patterns (see the section above); for this batch no component was searched or retrieved, because native elements covered tables, forms and dialogs | None. |

## Phase 5, slice 1 (proxy and catalogue wiring), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| API access | The browser calls `/api/*` on the Next.js origin; a `rewrites()` rule proxies it to `API_ORIGIN` (default `http://localhost:8080`). Server components call the API directly with the same variable | The rewrite destination is fixed at `next build`, so the production image needs the variable as a build argument (phase 7). Every browser API call passes through the Next server (one more hop); in return the session and cart cookies are first-party, no CORS, and the CSRF flow in `docs/api/auth.md` is unchanged. |
| Caching | Lists and counts: Next data cache with a 30 s revalidate; product detail: never cached; categories: 5 min. Pages that fetch are rendered per request, so `next build` does not need the API | Revalidation is stale-while-revalidate: after an idle period the first request is served the old entry while it refreshes, so a listing can show data older than 30 s (found when a stale entry from another run appeared). The product page and (later) the cart and checkout read fresh data. The screenshot and live-check scripts delete `web/.next` first. |
| Data the API does not have | Product cards show the category name, not a colour (the summary has none); the product page lists the colours of its variants and shows the description only. The fixtures' "material" and "detail bullets" never existed in the database: the seeded description already carries that copy | The colour filter was removed (no API parameter). The product page lost the Materials and Details accordions. |
| Occasions | An occasion maps to one or more API category slugs. The backend `category` parameter now accepts a comma-separated list (blank entries ignored, 200 characters at most); a parent still includes its children | "Everyday sneakers" is the `sneakers` category, which includes `chunky-sneakers`, so its count includes the "Street" tile's products. Either rename the tile ("Sneakers") or add an exact-match option later. |
| Filter counts | One small count query per filter option (about 25 per listing view, in parallel), because the search returns no facet counts | More API load per view than one counts endpoint would cause; not measured. Replace with a single endpoint if the measurement says so (`docs/api/proposed-ui-needs.md`). |
| Pagination | 24 per page, numbered links, `page` is 1-based in the URL and 0-based in the API; sorting is newest, price low to high, price high to low, and best match when searching ("Featured" had no API meaning and was removed) | Deep pages are bounded by the API's maximum page. |
| Review hooks | `?state=loading\|error\|empty` on the listing still exist so those states can be screenshotted; a real API failure shows the same error state, and anything else that throws lands on a route error page | The hooks are removed at the end of phase 5. |
| Screenshots | `scripts/design-shots.sh` runs against a mock of the three catalogue endpoints (`web/e2e/mock-api.mjs`, the fixtures cloned four times in the real response shapes), not the real API. `scripts/web-live-check.sh` runs five checks against the real API | The mock re-implements filtering and can drift from the backend; the live check is the evidence that matters. |
| Colour and size | A product's variants are size x colour (the fixtures had one colour each). With more than one colour the page shows colour pills first, then that colour's sizes (each size once); the "Colours" line appears only for single-colour products | Found by looking at real data: the first wired product page repeated every size once per colour. The gallery does not change with the colour (placeholders). |
| Screenshot port | The mock API uses its own port (8089, `MOCK_API_PORT`) and the script refuses to run if the mock does not answer with its 76 products | Found when a real API on 8080 silently answered the "mock" requests. |

## Phase 5, slice 2 (auth and cart wiring) and data fixes, settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Running shoes | The seeder's `running` category is road running only: "Carbon Racer" and "Marathon Racer" (carbon plate, race-day copy, $220 to $300) and "Road Trainer" and "Tempo Shoe" ($120 to $200, daily copy). The "Trail Runner" type and the "gusseted tongue keeps debris out" line are gone; racers and trainers are separate kinds so a racer never gets trainer copy. The Race day tile now reads "Road racers and daily trainers" | The owner's local database was seeded with the old rules (50,000 products) and keeps them until it is re-seeded: `docker compose down -v`, `docker compose up -d`, then the seed profile (this also deletes local customers and orders). The seeded random sequence changed, so product names differ after a re-seed. |
| Exact category match | `categoryExact=true` on `GET /api/products` matches the named categories only, not their children. The frontend sends it for every occasion, so "Everyday sneakers" no longer counts "Street" | One more query parameter. Without it a parent still includes its children (unchanged). |
| Browser API access | One client (`lib/api-client.ts`): same-origin `/api`, CSRF token fetched once and sent on every write, replaced by the token that login and register return, refetched and retried once on `csrf_invalid` | The token lives in memory; a page reload fetches a new one. |
| Session in the UI | `AuthProvider` reads `/api/auth/me` once; `undefined` while loading, so the header reserves the space instead of flashing "Sign in". Signed-in header is a native `<details>` menu (orders, admin for admins, sign out) with outside-click and Escape handling | `<details>` has no built-in light dismiss; two small listeners add it. |
| Cart | `CartProvider` reads `/api/cart` once the session is known and again when the customer changes (sign-in merges the guest cart server-side). Every write returns the whole cart, which replaces local state; add reads the current quantity and PUTs quantity + 1 (the API sets, not adds). Errors become words the screen owns (`insufficient_stock`, `unknown_item`, `max`, network) | Two quick clicks on "add" can race (both read the same quantity); the second PUT then sets the same number. Acceptable for one shopper; noted. |
| Review mode | `SeedCart` still fills the cart from fixtures for the `?state=` review URLs; once it does, the provider stops calling the API | The fixture mode and the `?state=` hooks go away at the end of phase 5. |
| Redirects | `?next=` accepts same-site paths only (starts with `/`, not `//`, no backslash); anything else goes home | |

## Phase 5, slice 3 (checkout, pay, confirmation wiring), settled 2026-11-15

| Decision | Choice | Tradeoff accepted |
| --- | --- | --- |
| Order read endpoint | `GET /api/orders/{id}` (signed in, owner only): the checkout order shape plus, per item, `productSlug` and `brand` (joined from the variant's product) and `history` (from `order_status_history`, oldest first). Someone else's order and an unknown id give the same 404 `order_not_found`; a guest gets 401 | The `orders` module reads `variants` and `products` by SQL for the links (no Java dependency on `catalog`, so Modulith is unaffected); `orders` now also depends on `auth` for the principal, like `checkout`. The order list for history is slice 4. |
| Where order screens fetch | In the browser (client components, session cookie automatic) rather than in server components | No server rendering of private pages (not wanted), and no cookie forwarding from the Next server to the API. Polling is trivial. |
| Confirmation | Polls `GET /api/orders/{id}` every 3 s while the order is `pending`; "Check again" reads once. No page text claims payment until the order says `paid` | A webhook delay longer than the page stays open shows "confirming" indefinitely; the order page and history show the truth later. |
| Idempotency key | One `Idempotency-Key` per form content: the same content reuses the key (a retry or double submit returns the same order), changed content gets a new one. The key lives in memory, so a reload starts a new attempt | A reload and resubmit creates a new order and cancels the previous pending one (the documented behaviour of a new key). |
| Cart changes at checkout | `cart_changed` marks lines by SKU (`unavailable` or `stock`); "Reduce to N" uses the live `maxQuantity` from the cart, "Remove" deletes the line; Place order stays disabled until none are left | The API's `stock` code carries no number, so N comes from a second source (the cart read). |
| Guests | Can fill the form; pressing Place order shows the sign-in prompt and sends nothing | Their typed details live only in the page; they are not restored after a sign-in redirect (the bag is). |
| Pay now | `POST /api/orders/{id}/pay`, then `window.location.assign(url)`. Errors: `payments_unavailable` and provider errors keep the order and offer a retry; `order_expired` or `payment_expired` show the expired panel; `order_not_payable` goes to the order page. A non-pending order never shows the pay step (paid goes to the confirmation) | The lapsed-hold check also uses the browser clock. |
| Review states | `?state=` still shows fixture orders on the three order pages and the checkout; without it the pages are live | Removed at the end of phase 5. |
| Pipelines | `design-shots.sh` and `web-live-check.sh` now refuse to start while the other runs or port 3000 is taken | Found when the two collided on port 3000, `web/.next` and `web/test-results` and corrupted a run. |
