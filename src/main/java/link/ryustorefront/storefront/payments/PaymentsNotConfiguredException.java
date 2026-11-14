package link.ryustorefront.storefront.payments;

/** No payment provider credentials are configured (for example no STRIPE_SECRET_KEY in the environment). */
public class PaymentsNotConfiguredException extends RuntimeException {

    public PaymentsNotConfiguredException() {
        super("No payment provider is configured");
    }
}
