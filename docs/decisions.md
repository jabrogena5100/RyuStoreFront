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
