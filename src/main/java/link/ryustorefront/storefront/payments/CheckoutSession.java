package link.ryustorefront.storefront.payments;

/** A payment page created at the provider: its id (as the webhook will report it) and where to send the shopper. */
public record CheckoutSession(String sessionId, String url) {
}
