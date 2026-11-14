package link.ryustorefront.storefront.checkout;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import link.ryustorefront.storefront.cart.CartLine;
import link.ryustorefront.storefront.cart.CustomerCart;
import link.ryustorefront.storefront.inventory.InsufficientStockException;
import link.ryustorefront.storefront.inventory.Reservation;
import link.ryustorefront.storefront.inventory.StockReservations;
import link.ryustorefront.storefront.orders.NewOrder;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a checkout: snapshot the cart's prices into a pending order and hold its stock, all in ONE
 * transaction. Either the order exists with every unit reserved, or nothing changed.
 *
 * <p>Order of work, under a per-customer lock so one customer's checkouts run one at a time:
 * <ol>
 *   <li>If this idempotency key already produced an order for this customer, return it.</li>
 *   <li>Reject an empty cart.</li>
 *   <li>Cancel the customer's other pending orders and give their stock back. This comes before the
 *       stock check on purpose: otherwise a retried or changed checkout would see its own earlier hold
 *       as "sold out" and could never take the last pair.</li>
 *   <li>Re-read the cart and validate it (every line on sale and in stock); report every problem at once.</li>
 *   <li>Create the order and its item snapshot, then reserve the stock (atomic conditional updates).</li>
 * </ol>
 * Any failure (including another buyer taking the last unit in between) rolls back all of it, including
 * the cancellation in step 3, so a failed attempt never destroys an earlier pending order.
 */
@Service
class CheckoutService {

    /** The outcome of starting a checkout. {@code created} is false when an idempotency key was replayed. */
    record Result(OrderSummary order, boolean created) {
    }

    private final Orders orders;
    private final CustomerCart carts;
    private final StockReservations stock;
    private final long shippingFlatMinor;
    private final Duration reservationTtl;

    CheckoutService(Orders orders, CustomerCart carts, StockReservations stock,
                    @Value("${storefront.checkout.shipping-flat-minor:800}") long shippingFlatMinor,
                    @Value("${storefront.checkout.reservation-ttl-minutes:30}") int reservationTtlMinutes) {
        this.orders = orders;
        this.carts = carts;
        this.stock = stock;
        this.shippingFlatMinor = shippingFlatMinor;
        this.reservationTtl = Duration.ofMinutes(reservationTtlMinutes);
    }

    @Transactional
    Result start(long customerId, String accountEmail, String idempotencyKey, StartCheckoutRequest request) {
        orders.lockCustomerCheckouts(customerId);

        Optional<OrderSummary> replay = orders.findByIdempotencyKey(customerId, idempotencyKey);
        if (replay.isPresent()) {
            return new Result(replay.get(), false);
        }

        if (carts.lines(customerId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "empty_cart", "Your bag is empty.");
        }

        for (UUID previous : orders.pendingOrderIds(customerId)) {
            // Order row first, then its reservations (the application-wide lock order, see Orders#transition).
            // If the order was paid in the meantime it is no longer pending: leave it and its stock alone.
            if (orders.cancelPending(previous, "superseded by a new checkout")) {
                stock.release(previous);
            }
        }

        // Read after releasing: the stock figures now exclude this customer's own earlier holds.
        List<CartLine> lines = carts.lines(customerId);
        Map<String, String> problems = new LinkedHashMap<>();
        for (CartLine line : lines) {
            if (!line.active()) {
                problems.put(line.sku(), "unavailable");
            } else if (line.stock() < line.quantity()) {
                problems.put(line.sku(), "stock");
            }
        }
        if (!problems.isEmpty()) {
            throw cartChanged(problems);
        }

        long subtotal = lines.stream()
                .mapToLong(l -> Math.multiplyExact(l.unitPriceMinor(), (long) l.quantity()))
                .reduce(0L, Math::addExact);
        String email = request.contactEmail() != null ? request.contactEmail() : accountEmail.toLowerCase(Locale.ROOT);
        UUID orderId = orders.createPending(new NewOrder(customerId, email, lines.get(0).currency(), subtotal,
                shippingFlatMinor, request.shippingAddress(), idempotencyKey,
                lines.stream().map(l -> new NewOrder.Item(l.variantId(), l.sku(), l.productName(), l.sizeLabel(),
                        l.colour(), l.unitPriceMinor(), l.quantity())).toList()));

        Map<Long, Integer> quantities = new LinkedHashMap<>();
        lines.forEach(l -> quantities.put(l.variantId(), l.quantity()));
        List<Reservation> reservations;
        try {
            reservations = stock.reserve(orderId, quantities, reservationTtl);
        } catch (InsufficientStockException e) {
            // Someone else took the stock between our check and the reservation.
            String sku = lines.stream().filter(l -> l.variantId() == e.variantId()).findFirst()
                    .map(CartLine::sku).orElse("unknown");
            throw cartChanged(Map.of(sku, "stock"));
        }
        Instant expiresAt = reservations.stream().map(Reservation::expiresAt).min(Comparator.naturalOrder()).orElseThrow();
        orders.setExpiry(orderId, expiresAt);

        return new Result(orders.find(orderId).orElseThrow(), true);
    }

    private static ApiException cartChanged(Map<String, String> problems) {
        return new ApiException(HttpStatus.CONFLICT, "cart_changed",
                "Some items in your bag are no longer available as requested.", problems);
    }
}
