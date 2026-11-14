package link.ryustorefront.storefront.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Published synchronously on the request thread after a customer registers or logs in and the
 * session is established. Other modules listen to it instead of being called by {@code auth}, so
 * the dependency points one way (for example the cart merges a guest cart into the customer's).
 *
 * <p>The request and response are included because listeners may need cookies the browser sent
 * (the guest cart token) and may need to clear them. A listener that throws would fail the login,
 * so listeners must catch and log their own failures.
 */
public record SignedInEvent(long customerId, HttpServletRequest request, HttpServletResponse response) {
}
