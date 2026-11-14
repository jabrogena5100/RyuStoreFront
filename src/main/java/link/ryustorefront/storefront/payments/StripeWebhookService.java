package link.ryustorefront.storefront.payments;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import link.ryustorefront.storefront.inventory.StockReservations;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.orders.TransitionResult;
import link.ryustorefront.storefront.outbox.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns Stripe's events into order changes. Stripe delivers at least once, retries, and does not promise
 * order, so every rule below is written to be safe when an event arrives twice, at the same moment as its
 * twin, or after the event that logically follows it.
 *
 * <p><b>One transaction per event.</b> The first statement records the event id ({@code ON CONFLICT DO NOTHING});
 * if that inserted nothing the event was seen before and the call is a no-op. A concurrent duplicate waits on
 * the primary key until the first copy commits (then does nothing) or rolls back (then does the work itself).
 * Any exception rolls back everything, including the event id, so Stripe's retry starts clean.
 *
 * <p><b>Lock order</b> is the application's: the order row first ({@code Orders.transition}), then
 * reservations. The cheap reads before it (order totals, the attached session id) are immutable once written.
 *
 * <p><b>Money safety.</b> We only ever mark an order paid when the amount and currency Stripe took equal the
 * order's totals. Money that cannot be matched to a fulfilable order (the order was already cancelled, the
 * amount differs, or the stock hold is gone) is recorded in {@code payments} and refunded through the outbox
 * ({@link PaymentRefundHandler}), so the shopper is never left charged for nothing.
 */
@Service
class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final JdbcClient jdbc;
    private final Orders orders;
    private final StockReservations stock;
    private final Outbox outbox;

    StripeWebhookService(JdbcClient jdbc, Orders orders, StockReservations stock, Outbox outbox) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.stock = stock;
        this.outbox = outbox;
    }

    @Transactional
    void process(StripeEvent event) {
        int recorded = jdbc.sql("INSERT INTO stripe_events (event_id, event_type) VALUES (:id, :type) "
                        + "ON CONFLICT (event_id) DO NOTHING")
                .param("id", event.id()).param("type", event.type()).update();
        if (recorded == 0) {
            log.info("Stripe event {} ({}) was already received; ignoring the repeat", event.id(), event.type());
            return;
        }
        switch (event.type()) {
            case "checkout.session.completed" -> {
                if ("paid".equals(event.paymentStatus())) {
                    paid(event);
                } else {
                    // Delayed payment methods complete the session unpaid; checkout.session.async_payment_* follows.
                    log.info("Session {} completed with payment_status {}; waiting for the payment result",
                            event.sessionId(), event.paymentStatus());
                }
            }
            case "checkout.session.async_payment_succeeded" -> paid(event);
            case "checkout.session.expired", "checkout.session.async_payment_failed" -> notPaid(event);
            default -> log.debug("Ignoring Stripe event type {}", event.type());
        }
        jdbc.sql("UPDATE stripe_events SET processed_at = now() WHERE event_id = :id")
                .param("id", event.id()).update();
    }

    // ---- money arrived ----------------------------------------------------------------------------

    private void paid(StripeEvent event) {
        UUID orderId = orderId(event);
        OrderSummary order = orderId == null ? null : orders.find(orderId).orElse(null);
        if (order == null) {
            log.warn("Stripe event {} ({}) names no order we know (metadata order_id {}); ignoring",
                    event.id(), event.type(), event.orderId());
            return;
        }
        if (event.paymentIntentId() == null || event.amountTotal() == null || event.currency() == null) {
            // Cannot happen for a paid payment-mode session; failing loudly (500, Stripe retries) beats guessing.
            throw new IllegalStateException("Paid session " + event.sessionId() + " has no payment intent, amount or currency");
        }

        boolean amountMatches = event.amountTotal() == order.totalMinor()
                && order.currency().equalsIgnoreCase(event.currency());
        if (!amountMatches) {
            amountMismatch(event, order);
            return;
        }

        TransitionResult result = orders.transition(orderId, OrderStatus.PAID, "payment received");
        switch (result.outcome()) {
            case APPLIED -> {
                recordPayment(orderId, event);
                if (stock.commit(orderId) == 0) {
                    // The hold is gone (released behind our back). We cannot ship what we no longer have.
                    log.error("Order {} was paid but its stock hold is gone; refunding", orderId);
                    requestRefund(orderId, event, "stock hold no longer held");
                    orders.transition(orderId, OrderStatus.REFUNDED, "stock no longer held at payment");
                }
            }
            case ALREADY -> log.info("Order {} is already paid; event {} changes nothing", orderId, event.id());
            case ILLEGAL -> {
                OrderStatus previous = result.previous();
                if (previous == OrderStatus.CANCELLED) {
                    // The shopper paid after the order was given up on (page expired, hold swept). Money first, then explain.
                    if (recordPayment(orderId, event)) {
                        log.warn("Late payment for cancelled order {}; refunding", orderId);
                        requestRefund(orderId, event, "payment arrived after the order was cancelled");
                    }
                } else {
                    // Fulfilled, shipped or refunded: an earlier event already handled this payment.
                    log.info("Order {} is {}; event {} changes nothing", orderId, previous, event.id());
                }
            }
            case NOT_FOUND -> log.warn("Order {} vanished while handling event {}", orderId, event.id());
        }
    }

    /** Money was taken but not the order's total: do not fulfil; give the stock back and refund what was taken. */
    private void amountMismatch(StripeEvent event, OrderSummary order) {
        UUID orderId = order.id();
        log.error("Order {} expects {} {} but Stripe took {} {} (event {}); cancelling and refunding",
                orderId, order.totalMinor(), order.currency(), event.amountTotal(), event.currency(), event.id());
        TransitionResult result = orders.transition(orderId, OrderStatus.CANCELLED, "payment amount mismatch");
        if (result.applied()) {
            stock.release(orderId);
        }
        if (result.reachedTarget()) {
            if (recordPayment(orderId, event)) {
                requestRefund(orderId, event, "amount paid does not match the order total");
            }
        } else {
            // The order is already paid or beyond: this is a second, odd payment. Leave it for a human.
            log.error("Mismatched payment {} on order {} which is {}; NOT refunded automatically",
                    event.paymentIntentId(), orderId, result.previous());
        }
    }

    // ---- no money ---------------------------------------------------------------------------------

    /** The session expired or its payment failed: give up the order, but only if this is the order's own session. */
    private void notPaid(StripeEvent event) {
        UUID orderId = orderId(event);
        if (orderId == null || orders.find(orderId).isEmpty()) {
            log.warn("Stripe event {} ({}) names no order we know; ignoring", event.id(), event.type());
            return;
        }
        String current = orders.checkoutSessionId(orderId).orElse(null);
        if (!Objects.equals(current, event.sessionId())) {
            log.warn("Event {} is about session {} but order {} is waiting on {}; ignoring",
                    event.id(), event.sessionId(), orderId, current);
            return;
        }
        TransitionResult result = orders.transition(orderId, OrderStatus.CANCELLED, event.type());
        if (result.applied()) {
            stock.release(orderId);
        } else {
            // Already paid (the "completed" event came first) or already cancelled: nothing to undo.
            log.info("Order {} is {}; {} changes nothing", orderId, result.previous(), event.type());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static UUID orderId(StripeEvent event) {
        try {
            return event.orderId() == null ? null : UUID.fromString(event.orderId());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Inserts the payment; false if the order or the PaymentIntent already has one (a repeat). */
    private boolean recordPayment(UUID orderId, StripeEvent event) {
        // No conflict target: either unique constraint (one payment per order, one per PaymentIntent) means "seen".
        return jdbc.sql("INSERT INTO payments (order_id, stripe_payment_intent_id, amount_minor, currency) "
                        + "VALUES (:order, :intent, :amount, :currency) ON CONFLICT DO NOTHING")
                .param("order", orderId).param("intent", event.paymentIntentId())
                .param("amount", event.amountTotal())
                .param("currency", event.currency().toUpperCase(Locale.ROOT)).update() == 1;
    }

    private void requestRefund(UUID orderId, StripeEvent event, String reason) {
        outbox.publish("payment", orderId.toString(), PaymentRefundHandler.EVENT_TYPE, Map.of(
                "orderId", orderId.toString(), "paymentIntentId", event.paymentIntentId(),
                "amountMinor", String.valueOf(event.amountTotal()), "reason", reason));
    }
}
