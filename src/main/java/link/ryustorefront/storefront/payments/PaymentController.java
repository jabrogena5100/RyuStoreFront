package link.ryustorefront.storefront.payments;

import java.time.Instant;
import java.util.UUID;
import link.ryustorefront.storefront.auth.CustomerPrincipal;
import link.ryustorefront.storefront.shared.ApiException;
import link.ryustorefront.storefront.shared.StructuredErrors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts payment for the signed-in customer's own pending order and returns the hosted payment page.
 * Repeating the call returns the same page while it is still valid. Needs the CSRF header like every POST.
 */
@RestController
@StructuredErrors
@RequestMapping("/api/orders")
class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    /** The response body. */
    record PaymentPage(String url, Instant expiresAt) {
    }

    private final PaymentService service;

    PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/{orderId}/pay")
    PaymentPage pay(@AuthenticationPrincipal CustomerPrincipal customer, @PathVariable UUID orderId) {
        try {
            PaymentService.PaymentStart start = service.start(customer.id(), orderId);
            return new PaymentPage(start.url(), start.expiresAt());
        } catch (PaymentsNotConfiguredException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "payments_unavailable",
                    "Payments are not available right now.");
        } catch (PaymentProviderException e) {
            log.error("Payment provider failed for order {}", orderId, e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "payment_provider_error",
                    "The payment provider could not start the payment. Try again in a moment.");
        }
    }
}
