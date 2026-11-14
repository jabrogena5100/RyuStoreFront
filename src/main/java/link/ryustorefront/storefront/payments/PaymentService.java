package link.ryustorefront.storefront.payments;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import link.ryustorefront.storefront.inventory.StockReservations;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts payment for a pending order: creates the hosted payment page and makes sure the stock hold
 * outlives it.
 *
 * <p><b>Why the hold is extended.</b> A Stripe Checkout Session must last at least 30 minutes from its
 * creation, but the hold taken at checkout start also lasts about 30 minutes from <em>that</em> moment,
 * which is earlier. If the hold simply ended with the order, a shopper who pays near the end of the page's
 * life could pay for stock that was already given back. So creating the page also pushes the order's hold
 * and reservations out to the page's expiry plus a grace period.
 *
 * <p>An order gets one payment page. If it has expired the shopper must start a new checkout; this bounds how
 * long stock can be held to the first page's life plus the grace period.
 *
 * <p>The order row is locked for the whole call, so two clicks or two tabs create one session, and the
 * lock order (order, then reservations) matches the rest of the application. The provider call happens
 * under that lock, which is acceptable here: one row, one call, with timeouts of 10 s and 20 s.
 */
@Service
class PaymentService {

    /** The payment page and when it stops working. */
    record PaymentStart(String url, Instant expiresAt) {
    }

    private final Orders orders;
    private final StockReservations stock;
    private final PaymentGateway gateway;
    private final String publicBaseUrl;
    private final Duration sessionLifetime;
    private final Duration holdGrace;

    PaymentService(Orders orders, StockReservations stock, PaymentGateway gateway,
                   @Value("${storefront.public-base-url:http://localhost:3000}") String publicBaseUrl,
                   @Value("${storefront.payment.session-minutes:31}") int sessionMinutes,
                   @Value("${storefront.payment.hold-grace-minutes:5}") int holdGraceMinutes) {
        this.orders = orders;
        this.stock = stock;
        this.gateway = gateway;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        this.sessionLifetime = Duration.ofMinutes(sessionMinutes);
        this.holdGrace = Duration.ofMinutes(holdGraceMinutes);
    }

    @Transactional
    PaymentStart start(long customerId, UUID orderId) {
        Orders.PaymentState state = orders.lockForPayment(orderId, customerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "order_not_found", "No such order."));
        if (state.status() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "order_not_payable",
                    "This order can no longer be paid (" + state.status().dbValue() + ").");
        }
        Instant now = Instant.now();
        if (state.sessionId() != null) {
            if (state.paymentExpiresAt() != null && state.paymentExpiresAt().isAfter(now)) {
                return new PaymentStart(state.paymentUrl(), state.paymentExpiresAt()); // already created: same page again
            }
            // One payment page per order. Creating another (and extending the hold again) would let a shopper
            // keep stock locked for ever by clicking; the hold is bounded by the first page plus its grace.
            throw new ApiException(HttpStatus.CONFLICT, "payment_expired",
                    "The payment page for this order has expired. Start a new checkout.");
        }
        if (state.expiresAt() == null || !state.expiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "order_expired",
                    "The time to pay for this order has run out. Start a new checkout.");
        }

        OrderSummary order = orders.find(orderId).orElseThrow();
        // Microseconds: that is what Postgres stores, so every response shows the same instant.
        Instant pageExpiresAt = now.plus(sessionLifetime).truncatedTo(ChronoUnit.MICROS);
        CheckoutSession session = gateway.createCheckoutSession(new CheckoutRequest(
                orderId, order.contactEmail(), order.currency(), lines(order), pageExpiresAt,
                publicBaseUrl + "/orders/" + orderId + "/confirmation?session_id={CHECKOUT_SESSION_ID}",
                publicBaseUrl + "/cart"));

        Instant holdUntil = pageExpiresAt.plus(holdGrace);
        orders.attachPayment(orderId, session.sessionId(), session.url(), pageExpiresAt);
        orders.extendHold(orderId, holdUntil);
        stock.extendHold(orderId, holdUntil);
        return new PaymentStart(session.url(), pageExpiresAt);
    }

    private static List<CheckoutRequest.Line> lines(OrderSummary order) {
        List<CheckoutRequest.Line> lines = new ArrayList<>();
        for (OrderSummary.Line item : order.items()) {
            lines.add(new CheckoutRequest.Line(item.productName() + " (size " + item.sizeLabel() + ", "
                    + item.colour() + ")", item.unitPriceMinor(), item.quantity()));
        }
        if (order.shippingMinor() > 0) {
            lines.add(new CheckoutRequest.Line("Shipping", order.shippingMinor(), 1));
        }
        return lines;
    }
}
