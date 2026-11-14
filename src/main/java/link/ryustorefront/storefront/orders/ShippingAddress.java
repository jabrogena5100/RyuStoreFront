package link.ryustorefront.storefront.orders;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Where an order ships. Stored as jsonb on the order; free text except the ISO 3166-1 alpha-2 country. */
public record ShippingAddress(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String line1,
        @Size(max = 100) String line2,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 60) String region,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "invalid_country") String country) {

    public ShippingAddress {
        name = trim(name);
        line1 = trim(line1);
        line2 = line2 == null || line2.isBlank() ? null : line2.trim();
        city = trim(city);
        region = trim(region);
        postalCode = trim(postalCode);
        country = trim(country);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
