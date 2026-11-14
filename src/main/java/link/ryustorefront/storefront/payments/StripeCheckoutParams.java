package link.ryustorefront.storefront.payments;

import com.stripe.param.checkout.SessionCreateParams;
import java.util.Locale;

/**
 * Turns a {@link CheckoutRequest} into Stripe's request. Separate from the network call so the mapping
 * (amounts, currency, line items, the order id Stripe will echo back in webhooks) can be tested offline.
 *
 * <p>No card data ever reaches this application: the shopper pays on Stripe's hosted page.
 */
final class StripeCheckoutParams {

    private StripeCheckoutParams() {
    }

    static SessionCreateParams from(CheckoutRequest request) {
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .setClientReferenceId(request.orderId().toString())
                .setCustomerEmail(request.customerEmail())
                .setExpiresAt(request.expiresAt().getEpochSecond())
                // The webhook finds the order from these, never from anything the shopper's browser sends.
                .putMetadata("order_id", request.orderId().toString());
        String currency = request.currency().toLowerCase(Locale.ROOT);
        for (CheckoutRequest.Line line : request.lines()) {
            builder.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) line.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(line.unitAmountMinor())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(line.name())
                                    .build())
                            .build())
                    .build());
        }
        return builder.build();
    }
}
