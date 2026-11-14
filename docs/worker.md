# Outbox and worker

Background work (cancelling abandoned checkouts, and from slice 4.3 sending email) runs in the **worker**:
the same jar as the API, started with the `worker` Spring profile. One image, two services (decision 3).
The API process never polls; the worker does. Both talk to the same Postgres.

```
 API request ──► one transaction: change the order  +  INSERT INTO outbox   (commit together, or neither)
                                                            │
 worker (profile "worker") ─ every second ─►  claim one due event (FOR UPDATE SKIP LOCKED)
                                              run its handler  ─►  mark processed      (one transaction)
                          ─ every 30 s ────►  cancel overdue pending orders, give their stock back
```

## Run it locally

```
set -a; source .env; set +a
docker compose up -d
./mvnw spring-boot:run                                                   # terminal 1: the API
./mvnw spring-boot:run -Dspring-boot.run.profiles=worker \
       -Dspring-boot.run.arguments=--server.port=8081                    # terminal 2: the worker
```

The worker keeps an HTTP port on purpose: ECS and the load balancer can health-check it. Locally it needs
a different port from the API.

## Delivery guarantees

- **At least once.** An event is marked processed in the same transaction as the handler's database work.
  If the process dies first, nothing is committed and the event is delivered again after restart.
  **Handlers must be idempotent.** Work outside the database (an email, an HTTP call) is not rolled back,
  so a handler that sends email must record that it did.
- **Several handlers per event type.** `order.paid` has two (cart clearing, then the email, ordered with `@Order`).
  They run one after another in the same transaction; if any throws, all of their database work is rolled back
  and the whole event is retried, so each must be idempotent on its own. Work outside the database (the email)
  is therefore ordered last.
- **Atomic with the change.** `Outbox.publish` refuses to run without a transaction, so an event exists if
  and only if the change that caused it committed. Every order status change publishes `order.<status>`.
- **Several workers are safe.** `FOR UPDATE SKIP LOCKED` means workers never wait for or duplicate each
  other; the expiry sweep is idempotent per order.
- **Oldest first**, per worker. There is no ordering guarantee across workers or after a retry.

## Failure handling

| Attempt | Delay before the next try |
| --- | --- |
| 1st failure | 5 s |
| 2nd | 10 s |
| 3rd | 20 s |
| ... | doubling, capped at 15 minutes |
| 10th failure | **dead**: never claimed again, kept in the table for inspection |

A failed attempt rolls back its own database work, then the failure is recorded in a separate step
(`attempts`, `last_error`, `available_at`). An event whose type has **no handler** is marked processed with
`last_error = 'no handler registered'` rather than retried forever.

Look at what is stuck:

```sql
SELECT id, event_type, attempts, last_error, available_at FROM outbox
WHERE processed_at IS NULL ORDER BY id;          -- pending, retrying, or dead (attempts >= 10)
```

## Known limits (recorded, not hidden)

- A **kill is not counted as an attempt** (the transaction never commits, so the counter never changes). An
  event that crashes the whole JVM every time would be retried forever. Nothing in the app does that today;
  alarms on pending-event age (phase 6) are the real guard.
- Jobs share a small scheduler pool (2 threads): a handler that blocks for a long time delays the sweep.
- Dead events are not yet surfaced as a metric or alarm (phase 6).
- Polling every second is simple and costs one cheap query per second per worker; there is no push wakeup.
