package link.ryustorefront.storefront.payments;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Checks Stripe's {@code Stripe-Signature} header against the raw request body: HMAC-SHA256 over
 * {@code "<timestamp>.<body>"} with the endpoint's signing secret, compared in constant time, and the
 * timestamp must be within the tolerance (default five minutes) so a captured request cannot be replayed
 * much later. Uses the SDK's verifier only, not its typed event parsing.
 */
@Component
class WebhookSignature {

    /** Why a request was refused. */
    static final class Rejected extends Exception {
        Rejected(String message) {
            super(message);
        }
    }

    private final String secret;
    private final long toleranceSeconds;

    WebhookSignature(@Value("${stripe.webhook-secret:}") String secret,
                     @Value("${storefront.payment.webhook-tolerance-seconds:300}") long toleranceSeconds) {
        this.secret = secret == null ? "" : secret.trim();
        this.toleranceSeconds = toleranceSeconds;
    }

    boolean configured() {
        return !secret.isEmpty();
    }

    /** @throws Rejected if the header is missing, malformed, stale or does not match the body */
    void verify(byte[] body, String header) throws Rejected {
        if (header == null || header.isBlank()) {
            throw new Rejected("missing Stripe-Signature header");
        }
        try {
            if (!Webhook.Signature.verifyHeader(new String(body, StandardCharsets.UTF_8), header, secret,
                    toleranceSeconds)) {
                throw new Rejected("signature does not match");
            }
        } catch (SignatureVerificationException e) {
            throw new Rejected(e.getMessage());
        } catch (RuntimeException e) {
            // A header the SDK's parser chokes on (for example a non-numeric timestamp) is still just a bad signature.
            throw new Rejected("malformed Stripe-Signature header: " + e);
        }
    }
}
