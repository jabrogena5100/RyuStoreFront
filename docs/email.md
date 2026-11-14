# Email

Transactional email is sent by the worker, from outbox events (see `docs/worker.md`). Slice 4.3 sends one
email: the **order confirmation**, when an order becomes `paid` (event `order.paid`).

## Local: Mailpit

`docker compose up -d` starts Mailpit (`axllent/mailpit:v1.33.1`). It accepts SMTP on `localhost:1025`, and
**nothing leaves your machine**. Open the inbox at <http://localhost:8025>. The application's SMTP settings
(`spring.mail.*`) already point there. The sender is `storefront.mail.from` (default `orders@storefront.local`).

In AWS this becomes SES (sandbox first); only the `spring.mail.*` settings change, not the code.

## How a confirmation is sent

1. `Orders.transition(order, PAID)` writes the status change and the `order.paid` event in one transaction.
2. The worker claims the event and runs `OrderPaidEmailHandler`.
3. The handler inserts a row into `sent_emails` keyed `order.paid:<order id>` (`ON CONFLICT DO NOTHING`). If
   the row already exists the email was sent before and the handler stops: **redelivery sends no second email**.
4. Otherwise it sends multipart text and HTML. If the mail server fails, the handler throws, the ledger row is
   rolled back with the rest of the transaction, and the event is retried with backoff.

## Content and safety

`OrderConfirmationEmail` builds the message and has no I/O, so it is unit-tested. Names, address lines and
product names are HTML-escaped in the HTML part (a product called `<script>` cannot inject markup); the text
part is plain text. Money is shown as `129.90 USD` from the order's own snapshot, not from the live catalogue.

## Known limits (recorded, not hidden)

- **A crash after the mail server accepts the message but before the database commit sends a second copy** on
  retry. Sending twice was chosen over possibly never sending; the window is one SMTP call.
- SMTP is deliberately **not** part of `/actuator/health`: a mail outage delays emails but must not take the
  shop out of the load balancer.
- Only the confirmation exists. Shipped, refunded and payment-failed emails come with the admin area.
- No unsubscribe or marketing mail: transactional only.
