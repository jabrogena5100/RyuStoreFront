package link.ryustorefront.storefront.orders;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The order lifecycle. The rules live here, in one table, and nowhere else:
 *
 * <pre>
 *   PENDING --> PAID --> FULFILLED --> SHIPPED
 *      |          |          |            |
 *      v          v          v            v
 *  CANCELLED   REFUNDED   REFUNDED     REFUNDED
 * </pre>
 *
 * A paid order is never "cancelled": giving the money back is a refund. CANCELLED and REFUNDED are
 * terminal, and nothing ever moves back to PENDING.
 */
public enum OrderStatus {
    PENDING, PAID, FULFILLED, SHIPPED, CANCELLED, REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING, Set.of(PAID, CANCELLED));
        ALLOWED.put(PAID, Set.of(FULFILLED, REFUNDED));
        ALLOWED.put(FULFILLED, Set.of(SHIPPED, REFUNDED));
        ALLOWED.put(SHIPPED, Set.of(REFUNDED));
        ALLOWED.put(CANCELLED, Set.of());
        ALLOWED.put(REFUNDED, Set.of());
    }

    /** True only for a legal move to a different status; staying in the same status is not a transition. */
    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    public Set<OrderStatus> allowedNext() {
        return ALLOWED.get(this);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** The lowercase value stored in {@code orders.status} (see the CHECK constraint in V3). */
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OrderStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
