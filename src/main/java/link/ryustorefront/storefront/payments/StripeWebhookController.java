package link.ryustorefront.storefront.payments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import link.ryustorefront.storefront.shared.ApiException;
import link.ryustorefront.storefront.shared.StructuredErrors;

/**
 * Stripe's webhook endpoint. Public (Stripe has no session or CSRF token): the only credential is the
 * signature, checked on the raw bytes before anything else is read. The body is taken as {@code byte[]} so
 * no converter re-encodes it; a re-serialised body would not match the signature.
 *
 * <p>Answers 200 for every verified event, including types and orders we ignore, so Stripe stops retrying;
 * 400 for a bad signature or an unreadable body; 503 if no signing secret is configured (never accept
 * unsigned input); 500 (from an exception) when processing failed, so Stripe retries.
 */
@RestController
@StructuredErrors
@RequestMapping("/api/stripe")
class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final WebhookSignature signature;
    private final StripeWebhookService service;

    StripeWebhookController(WebhookSignature signature, StripeWebhookService service) {
        this.signature = signature;
        this.service = service;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    void receive(@RequestBody(required = false) byte[] body,
                 @RequestHeader(name = "Stripe-Signature", required = false) String header) {
        if (!signature.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "webhook_unavailable",
                    "The webhook is not configured on this server.");
        }
        byte[] raw = body == null ? new byte[0] : body;
        try {
            signature.verify(raw, header);
        } catch (WebhookSignature.Rejected e) {
            log.warn("Rejected a Stripe webhook: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_signature", "The signature could not be verified.");
        }
        StripeEvent event;
        try {
            event = StripeEvent.parse(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            log.warn("A correctly signed webhook had an unreadable body: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "malformed_event", "The event could not be read.");
        }
        service.process(event);
    }
}
