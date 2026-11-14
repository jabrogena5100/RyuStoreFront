package link.ryustorefront.storefront.payments;

import java.util.UUID;
import link.ryustorefront.storefront.outbox.OutboxEvent;
import link.ryustorefront.storefront.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Carries out a queued refund. The webhook decides that money must go back (late payment, amount mismatch,
 * stock gone) and queues {@code payment.refund_requested}; this handler asks the provider and then marks the
 * payments row refunded.
 *
 * <p>Idempotent: the provider call uses the idempotency key {@code refund-order-<orderId>}, and the row update
 * only touches a row not yet refunded. If the provider call throws, the event is retried with backoff.
 */
@Component
class PaymentRefundHandler implements OutboxHandler {

    static final String EVENT_TYPE = "payment.refund_requested";

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundHandler.class);

    private final PaymentGateway gateway;
    private final JdbcClient jdbc;

    PaymentRefundHandler(PaymentGateway gateway, JdbcClient jdbc) {
        this.gateway = gateway;
        this.jdbc = jdbc;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(OutboxEvent event) {
        UUID orderId = UUID.fromString(event.payload().get("orderId"));
        String paymentIntentId = event.payload().get("paymentIntentId");
        gateway.refund(paymentIntentId, "refund-order-" + orderId);
        jdbc.sql("""
                UPDATE payments SET refunded_minor = amount_minor, status = 'refunded', updated_at = now()
                WHERE order_id = :order AND status <> 'refunded'""")
                .param("order", orderId).update();
        log.info("Refunded payment {} for order {} ({})", paymentIntentId, orderId, event.payload().get("reason"));
    }
}
