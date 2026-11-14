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
