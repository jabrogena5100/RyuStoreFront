package link.ryustorefront.storefront.notifications;

import java.util.UUID;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.outbox.OutboxEvent;
import link.ryustorefront.storefront.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Sends the order confirmation when an order is paid.
 *
 * <p>The outbox delivers at least once, so this handler guards against sending twice: it first records
 * the email in {@code sent_emails} (a primary key on the dedupe key), and sends only if that insert is
 * new. The record and the send share the worker's transaction, so if sending fails the record is rolled
 * back and the event is retried with backoff. A permanent refusal ({@link UndeliverableEmailException}, such as an
 * unverified recipient in the SES sandbox) is logged and not retried, so it cannot hold back the cart clearing that
 * shares this event's transaction.
 *
 * <p>The one remaining window is a crash after the mail server accepted the message but before the
 * transaction commits: the retry then sends a second copy. Sending twice was chosen over possibly never
 * sending, and the window is the length of one call to the mail service (SMTP or SES).
 */
@Component
class OrderPaidEmailHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidEmailHandler.class);

    private final Orders orders;
    private final EmailSender mail;
    private final JdbcClient jdbc;
    private final String from;

    OrderPaidEmailHandler(Orders orders, EmailSender mail, JdbcClient jdbc,
                          @Value("${storefront.mail.from:orders@storefront.local}") String from) {
        this.orders = orders;
        this.mail = mail;
        this.jdbc = jdbc;
        this.from = from;
    }

    @Override
    public String eventType() {
        return "order.paid";
    }

    @Override
    public void handle(OutboxEvent event) {
        UUID orderId = UUID.fromString(event.payload().get("orderId"));
        OrderSummary order = orders.find(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not exist"));
        // A payment whose stock hold was gone is paid and then refunded in one transaction (see the Stripe
        // webhook); the shopper must not be told the order is confirmed.
        OrderStatus status = OrderStatus.fromDb(order.status());
        if (status == OrderStatus.REFUNDED || status == OrderStatus.CANCELLED) {
            log.info("Order {} is {}; sending no confirmation", orderId, order.status());
            return;
        }
        EmailContent content = OrderConfirmationEmail.build(order);

        int recorded = jdbc.sql("INSERT INTO sent_emails (dedupe_key, recipient, subject) "
                        + "VALUES (:key, :to, :subject) ON CONFLICT (dedupe_key) DO NOTHING")
                .param("key", "order.paid:" + orderId).param("to", order.contactEmail())
                .param("subject", content.subject()).update();
        if (recorded == 0) {
            log.info("Confirmation for order {} was already sent; skipping", orderId);
            return;
        }
        try {
            mail.send(from, order.contactEmail(), content);
        } catch (UndeliverableEmailException e) {
            // Permanent refusal: keep no "sent" record, and let the event (and the cart clearing) complete.
            jdbc.sql("DELETE FROM sent_emails WHERE dedupe_key = :key").param("key", "order.paid:" + orderId).update();
            log.warn("Confirmation for order {} not delivered: {}", orderId, e.getMessage());
            return;
        }
        log.info("Sent confirmation for order {}", orderId);
    }
}
