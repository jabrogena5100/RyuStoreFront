package link.ryustorefront.storefront.payments;

/**
 * The payment provider, behind an interface so everything around it can be tested without the network.
 * The real implementation talks to Stripe; tests install a fake.
 */
public interface PaymentGateway {

    /**
     * Creates a hosted payment page for an order.
     *
     * @throws PaymentsNotConfiguredException if no provider credentials are configured
     * @throws PaymentProviderException if the provider rejected or failed the request
     */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    /**
     * Refunds the whole payment behind a PaymentIntent. Must be safe to repeat with the same
     * {@code idempotencyKey} (the outbox delivers at least once): the provider treats a repeat as the same
     * refund, and a payment that is already fully refunded counts as success.
     *
     * @throws PaymentsNotConfiguredException if no provider credentials are configured
     * @throws PaymentProviderException if the provider rejected or failed the request
     */
    void refund(String paymentIntentId, String idempotencyKey);
}
