package link.ryustorefront.storefront.cart;

import link.ryustorefront.storefront.auth.SignedInEvent;
import link.ryustorefront.storefront.cart.internal.GuestCartCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** On sign-in or registration, folds the browser's guest cart into the customer's cart. */
@Component
class CartMergeListener {

    private static final Logger log = LoggerFactory.getLogger(CartMergeListener.class);

    private final CartService service;
    private final GuestCartCookie cookie;

    CartMergeListener(CartService service, GuestCartCookie cookie) {
        this.service = service;
        this.cookie = cookie;
    }

    @EventListener
    void onSignedIn(SignedInEvent event) {
        var token = cookie.readToken(event.request());
        if (token.isEmpty()) {
            return;
        }
        try {
            service.mergeGuestIntoCustomer(GuestCartCookie.hash(token.get()), event.customerId());
            // Merged or not found: either way the cookie is now useless to this browser.
            event.response().addHeader(HttpHeaders.SET_COOKIE, cookie.expire());
        } catch (RuntimeException e) {
            // Never fail a login over the cart. The transaction rolled back, so the guest cart and its
            // cookie are untouched and the merge is retried at the next sign-in.
            log.error("Could not merge the guest cart for customer {}", event.customerId(), e);
        }
    }
}
