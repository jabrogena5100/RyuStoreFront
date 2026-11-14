package link.ryustorefront.storefront.payments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the payment provider needs to show a payment page for one order. Amounts are the order's
 * own snapshot in minor units; {@code expiresAt} is when the payment page stops working.
 */
public record CheckoutRequest(
        UUID orderId,
        String customerEmail,
        String currency,
        List<Line> lines,
        Instant expiresAt,
        String successUrl,
        String cancelUrl) {

    /** One line on the payment page. Shipping is sent as an ordinary line. */
    public record Line(String name, long unitAmountMinor, int quantity) {
    }
}
