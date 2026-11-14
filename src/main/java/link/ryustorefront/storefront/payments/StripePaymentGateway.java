package link.ryustorefront.storefront.payments;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The real gateway: Stripe Checkout (hosted page) in whatever mode the configured key is in. With no key
 * configured every call fails with {@link PaymentsNotConfiguredException}, so the application still
 * starts and the rest of the shop works without payment credentials.
 */
@Component
class StripePaymentGateway implements PaymentGateway {

    private static final RequestOptions TIMEOUTS = RequestOptions.builder()
            .setConnectTimeout(10_000)
            .setReadTimeout(20_000)
            .build();

    private final StripeClient client;

    StripePaymentGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.client = secretKey == null || secretKey.isBlank() ? null : new StripeClient(secretKey);
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        if (client == null) {
            throw new PaymentsNotConfiguredException();
        }
        try {
            Session session = client.checkout().sessions().create(StripeCheckoutParams.from(request), TIMEOUTS);
            return new CheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new PaymentProviderException("Stripe rejected the checkout session request: " + e.getMessage(), e);
        }
    }
}
