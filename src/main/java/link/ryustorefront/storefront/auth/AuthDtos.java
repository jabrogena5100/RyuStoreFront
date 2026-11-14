package link.ryustorefront.storefront.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes of {@code /api/auth}. Documented in docs/api/auth.md. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Emails are limited to 100 printable ASCII characters: Spring Session stores the principal name
     * in a VARCHAR(100) column, and ASCII keeps Java's lowercasing identical to Postgres' lower().
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 100) @Email @Pattern(regexp = "^[\\x21-\\x7E]+$", message = "invalid_email") String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @Size(max = 80) String displayName) {

        public RegisterRequest {
            email = email == null ? null : email.trim();
            displayName = displayName == null || displayName.isBlank() ? null : displayName.trim();
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record CustomerView(long id, String email, String displayName, String role) {

        static CustomerView of(CustomerPrincipal p) {
            return new CustomerView(p.id(), p.email(), p.displayName(), p.role());
        }
    }

    public record CsrfView(String headerName, String token) {
    }

    public record SessionView(CustomerView customer, CsrfView csrf) {
    }
}
