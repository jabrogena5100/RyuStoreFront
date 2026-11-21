# Email

Transactional email is sent by the worker, from outbox events (see `docs/worker.md`). Slice 4.3 sends one
email: the **order confirmation**, when an order becomes `paid` (event `order.paid`).

## Local: Mailpit

`docker compose up -d` starts Mailpit (`axllent/mailpit:v1.33.1`). It accepts SMTP on `localhost:1025`, and
**nothing leaves your machine**. Open the inbox at <http://localhost:8025>. The application's SMTP settings
(`spring.mail.*`) already point there. The sender is `storefront.mail.from` (default `orders@storefront.local`).

## AWS: SES API (phase 6)

`storefront.mail.transport` picks the sender: `smtp` (default, `SmtpEmailSender`, Mailpit locally) or `ses`
(`SesEmailSender`, Amazon SES API v2 `SendEmail` with simple content: subject, text and HTML parts, UTF-8). The worker
task sets `STOREFRONT_MAIL_TRANSPORT=ses` and `STOREFRONT_MAIL_FROM=orders@api.ryustorefront.com`; credentials are the
ECS task role, whose policy allows only `ses:SendEmail` with that From address. No SMTP password or access key exists.

- The sending domain `api.ryustorefront.com` is an SES identity in `infra/foundation/email.tf`, verified by three Easy DKIM
  records in the Route 53 zone, plus a monitoring-only DMARC record (`p=none`).
- The account is in the **SES sandbox**: mail goes only to verified addresses. The owner verifies their own address with the
  AWS CLI (`infra/README.md` 3A.4) and registers in the shop with it. Production access is a request to AWS; not planned.
- A rejected send (unverified recipient, throttling) throws, so the handler's `sent_emails` row rolls back and the event is
  retried with backoff, exactly like an SMTP failure. Tested in `SesEmailSenderTests` on the request the sender really builds.

## How a confirmation is sent

1. `Orders.transition(order, PAID)` writes the status change and the `order.paid` event in one transaction.
2. The worker claims the event and runs its handlers; `OrderPaidEmailHandler` is one (the cart-clearing handler is
   the other, see `docs/payments.md`). If the order is by now `refunded` or `cancelled` (the webhook paid it and
   refunded it in one transaction because its stock hold was gone) the handler sends nothing.
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
