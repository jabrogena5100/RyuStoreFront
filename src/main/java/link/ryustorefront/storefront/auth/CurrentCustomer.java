package link.ryustorefront.storefront.auth;

import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** How cart and checkout ask "who is signed in?" without reaching into Spring Security. */
public final class CurrentCustomer {

    private CurrentCustomer() {
    }

    /** The signed-in customer of the current request, or empty for guests. */
    public static Optional<CustomerPrincipal> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return auth.getPrincipal() instanceof CustomerPrincipal p ? Optional.of(p) : Optional.empty();
    }
}
