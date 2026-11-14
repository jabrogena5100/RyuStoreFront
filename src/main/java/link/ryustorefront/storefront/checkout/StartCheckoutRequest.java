package link.ryustorefront.storefront.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import link.ryustorefront.storefront.orders.ShippingAddress;

/**
 * {@code contactEmail} is optional and defaults to the account email. Same rules as registration:
 * at most 100 printable ASCII characters.
 */
public record StartCheckoutRequest(
        @Size(max = 100) @Email @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "invalid_email") String contactEmail,
        @NotNull @Valid ShippingAddress shippingAddress) {

    public StartCheckoutRequest {
        contactEmail = contactEmail == null || contactEmail.isBlank() ? null : contactEmail.trim();
    }
}
