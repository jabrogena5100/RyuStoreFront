package link.ryustorefront.storefront.checkout;

import java.util.UUID;
import link.ryustorefront.storefront.cart.CustomerCart;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.outbox.OutboxEvent;
import link.ryustorefront.storefront.outbox.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Empties the purchased lines from the customer's cart once the order is paid (not before: a shopper who
 * abandons or fails payment keeps their cart).
 *
 * <p>Idempotent: deleting lines that are already gone does nothing. Lines for variants the customer added
 * after checkout stay. {@code @Order(10)} runs it before the email handler (default order), so the one
 * step with an effect outside the database is last in the shared transaction.
 *
 * <p>It lives in {@code checkout} because that module already knows both orders and carts.
 */
@Component
@Order(10)
class OrderPaidCartHandler implements OutboxHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidCartHandler.class);

    private final Orders orders;
    private final CustomerCart cart;

    OrderPaidCartHandler(Orders orders, CustomerCart cart) {
        this.orders = orders;
        this.cart = cart;
    }

    @Override
    public String eventType() {
        return "order.paid";
    }

    @Override
    public void handle(OutboxEvent event) {
        UUID orderId = UUID.fromString(event.payload().get("orderId"));
        OrderSummary order = orders.find(orderId).orElse(null);
        if (order == null) {
            log.warn("order.paid for unknown order {}; nothing to clear", orderId);
            return;
        }
        // An order paid and then refunded (stock gone, see the webhook) was not bought: keep the cart.
        OrderStatus status = OrderStatus.fromDb(order.status());
        if (status == OrderStatus.REFUNDED || status == OrderStatus.CANCELLED) {
            log.info("Order {} is {}; leaving the cart alone", orderId, order.status());
            return;
        }
        orders.purchase(orderId).ifPresent(purchase -> {
            if (purchase.customerId() != null) {
                cart.removeLines(purchase.customerId(), purchase.variantIds());
            }
        });
    }
}
