package link.ryustorefront.storefront.orders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import link.ryustorefront.storefront.orders.internal.OrderRepository;
import link.ryustorefront.storefront.outbox.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What other modules may do with orders. Every method joins the caller's transaction, so checkout can
 * create an order and reserve its stock atomically.
 *
 * <p>Status changes go through {@link #transition}, which enforces the lifecycle in {@link OrderStatus}.
 */
@Service
public class Orders {

    private final OrderRepository orders;
    private final Outbox outbox;

    Orders(OrderRepository orders, Outbox outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /** Takes a transaction-scoped lock so one customer's checkouts run one at a time. */
    @Transactional
    public void lockCustomerCheckouts(long customerId) {
        orders.lockCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public Optional<OrderSummary> findByIdempotencyKey(long customerId, String key) {
        return orders.findIdByKey(customerId, key).flatMap(orders::find);
    }

    @Transactional(readOnly = true)
    public List<UUID> pendingOrderIds(long customerId) {
        return orders.pendingOrderIds(customerId);
    }

    /** Pending orders whose hold has run out, oldest first. The caller cancels them with {@link #cancelPending}. */
    @Transactional(readOnly = true)
    public List<UUID> overduePendingIds(int limit) {
        return orders.overduePendingIds(limit);
    }

    /** Inserts the order, its item snapshot and a null-to-pending history row. */
    @Transactional
    public UUID createPending(NewOrder order) {
        UUID id = orders.insertOrder(order);
        order.items().forEach(item -> orders.insertItem(id, item));
        orders.insertHistory(id, null, "pending", "checkout started");
        return id;
    }

    /** Records when the order stops holding stock (the reservation expiry). */
    @Transactional
    public void setExpiry(UUID orderId, Instant expiresAt) {
        orders.setExpiry(orderId, expiresAt);
    }

    /**
     * The one way an order changes status. Locks the order row, applies the rules in {@link OrderStatus},
     * writes a history row, and reports what happened (see {@link TransitionResult}). Safe to call
     * twice and from concurrent requests: the second caller sees the first one's result and gets
     * {@code ALREADY} (same target) or {@code ILLEGAL} (a different, now impossible target).
     *
     * <p><b>Lock order</b> everywhere in the application: the order row first, then its reservations,
     * then variants. Anything that changes an order and its stock (checkout superseding, webhook
     * handling, the expiry sweep) must call this before touching reservations, or two transactions can
     * wait on each other.
     */
    @Transactional
    public TransitionResult transition(UUID orderId, OrderStatus to, String reason) {
        Optional<String> current = orders.lockStatus(orderId);
        if (current.isEmpty()) {
            return new TransitionResult(TransitionResult.Outcome.NOT_FOUND, null);
        }
        OrderStatus from = OrderStatus.fromDb(current.get());
        if (from == to) {
            return new TransitionResult(TransitionResult.Outcome.ALREADY, from);
        }
        if (!from.canTransitionTo(to)) {
            return new TransitionResult(TransitionResult.Outcome.ILLEGAL, from);
        }
        orders.updateStatus(orderId, to.dbValue());
        orders.insertHistory(orderId, from.dbValue(), to.dbValue(), reason);
        // Same transaction as the status change: the event exists if and only if the change committed.
        outbox.publish("order", orderId.toString(), "order." + to.dbValue(), Map.of(
                "orderId", orderId.toString(), "from", from.dbValue(), "to", to.dbValue(),
                "reason", reason == null ? "" : reason));
        return new TransitionResult(TransitionResult.Outcome.APPLIED, from);
    }

    /** Cancels a pending order; false if it was no longer pending (paid, already cancelled, ...). */
    @Transactional
    public boolean cancelPending(UUID orderId, String reason) {
        return transition(orderId, OrderStatus.CANCELLED, reason).applied();
    }

    /** An order's payment state as the pay step needs it. {@code sessionId} is null until a session is attached. */
    public record PaymentState(OrderStatus status, Instant expiresAt, String sessionId, String paymentUrl,
                               Instant paymentExpiresAt) {
    }

    /**
     * Locks the customer's own order for the rest of the transaction and returns its payment state; empty if
     * there is no such order or it belongs to someone else (callers answer "not found" for both, so one
     * customer cannot probe another's order ids). Take this lock before touching reservations.
     */
    @Transactional
    public Optional<PaymentState> lockForPayment(UUID orderId, long customerId) {
        return orders.lockForPayment(orderId, customerId).map(r -> new PaymentState(OrderStatus.fromDb(r.status()),
                r.expiresAt(), r.sessionId(), r.paymentUrl(), r.paymentExpiresAt()));
    }

    /** Stores the payment session on a pending order. */
    @Transactional
    public boolean attachPayment(UUID orderId, String sessionId, String url, Instant paymentExpiresAt) {
        return orders.attachPayment(orderId, sessionId, url, paymentExpiresAt);
    }

    /** Moves a pending order's hold expiry later (never earlier). */
    @Transactional
    public boolean extendHold(UUID orderId, Instant newExpiresAt) {
        return orders.extendHold(orderId, newExpiresAt);
    }

    /**
     * The payment session attached to the order, if any. Written once and never changed, so reading it
     * without the row lock is safe. The webhook uses it to ignore events about any other session.
     */
    @Transactional(readOnly = true)
    public Optional<String> checkoutSessionId(UUID orderId) {
        return orders.checkoutSessionId(orderId);
    }

    /** Who bought and which variants; {@code customerId} is null for an order with no customer. */
    public record Purchase(Long customerId, List<Long> variantIds) {
    }

    @Transactional(readOnly = true)
    public Optional<Purchase> purchase(UUID orderId) {
        return orders.purchase(orderId).map(r -> new Purchase(r.customerId(), r.variantIds()));
    }

    @Transactional(readOnly = true)
    public Optional<OrderSummary> find(UUID orderId) {
        return orders.find(orderId);
    }
}
