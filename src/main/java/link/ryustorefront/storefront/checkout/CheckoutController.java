package link.ryustorefront.storefront.checkout;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.regex.Pattern;
import link.ryustorefront.storefront.auth.CustomerPrincipal;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.shared.ApiException;
import link.ryustorefront.storefront.shared.StructuredErrors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a checkout for the signed-in customer. Guests can fill a cart but must sign in to buy (the
 * security rules answer a guest with 401 before this runs). A retry with the same {@code Idempotency-Key}
 * returns the same order instead of creating another.
 */
@RestController
@StructuredErrors
@RequestMapping("/api/checkout")
class CheckoutController {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.:-]{8,100}$");

    private final CheckoutService service;

    CheckoutController(CheckoutService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<OrderSummary> start(@AuthenticationPrincipal CustomerPrincipal customer,
                                       @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                       @Valid @RequestBody StartCheckoutRequest body) {
        if (key == null || key.isBlank()) {
            throw badKey("required");
        }
        if (!KEY.matcher(key).matches()) {
            throw badKey("invalid_format");
        }
        CheckoutService.Result result = service.start(customer.id(), customer.email(), key, body);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
    }

    private static ApiException badKey(String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed",
                "A valid Idempotency-Key header is required.", Map.of("idempotencyKey", code));
    }
}
