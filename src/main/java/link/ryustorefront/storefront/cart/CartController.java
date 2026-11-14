package link.ryustorefront.storefront.cart;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import link.ryustorefront.storefront.auth.CurrentCustomer;
import link.ryustorefront.storefront.auth.CustomerPrincipal;
import link.ryustorefront.storefront.cart.CartDtos.CartView;
import link.ryustorefront.storefront.cart.CartDtos.SetQuantityRequest;
import link.ryustorefront.storefront.cart.internal.CartRepository.Variant;
import link.ryustorefront.storefront.cart.internal.GuestCartCookie;
import link.ryustorefront.storefront.shared.ApiException;
import link.ryustorefront.storefront.shared.StructuredErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The cart of the signed-in customer, or of the guest identified by the {@code CART} cookie. Reading
 * never creates a cart or a cookie; the first write does. Lines are addressed by SKU because that is
 * what the catalogue API exposes. Quantities are set, not incremented, so a retried request is harmless.
 */
@RestController
@StructuredErrors
@RequestMapping("/api/cart")
class CartController {

    private static final int MAX_SKU_LENGTH = 100;

    private final CartService service;
    private final GuestCartCookie cookie;

    CartController(CartService service, GuestCartCookie cookie) {
        this.service = service;
        this.cookie = cookie;
    }

    @GetMapping
    CartView get(HttpServletRequest request) {
        return existingCart(request).map(service::view).orElseGet(CartView::empty);
    }

    @PutMapping("/items/{sku}")
    CartView put(@PathVariable String sku, @Valid @RequestBody SetQuantityRequest body,
                 HttpServletRequest request, HttpServletResponse response) {
        Variant variant = variantFor(sku).filter(Variant::active)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "unknown_item", "This product is not available."));
        int quantity = body.quantity();
        if (quantity == 0) {
            return removeLine(variant, request, response);
        }
        if (variant.stock() < quantity) {
            throw new ApiException(HttpStatus.CONFLICT, "insufficient_stock",
                    "Not enough stock for that quantity.", Map.of("quantity", "stock"));
        }
        UUID cartId = existingOrNewCart(request, response);
        service.setQuantity(cartId, variant.id(), quantity);
        return service.view(cartId);
    }

    /** Idempotent: removing a line that is not there, or an unknown SKU, is not an error. */
    @DeleteMapping("/items/{sku}")
    CartView delete(@PathVariable String sku, HttpServletRequest request, HttpServletResponse response) {
        return variantFor(sku)
                .map(variant -> removeLine(variant, request, response))
                .orElseGet(() -> get(request));
    }

    private CartView removeLine(Variant variant, HttpServletRequest request, HttpServletResponse response) {
        Optional<UUID> cartId = existingCart(request);
        if (cartId.isEmpty()) {
            return CartView.empty();
        }
        renewGuestCookie(request, response);
        service.remove(cartId.get(), variant.id());
        return service.view(cartId.get());
    }

    private Optional<Variant> variantFor(String sku) {
        return sku.length() > MAX_SKU_LENGTH ? Optional.empty() : service.findVariant(sku);
    }

    private Optional<UUID> existingCart(HttpServletRequest request) {
        Optional<CustomerPrincipal> customer = CurrentCustomer.get();
        if (customer.isPresent()) {
            return service.findForCustomer(customer.get().id());
        }
        return cookie.readToken(request).flatMap(token -> service.findForGuest(GuestCartCookie.hash(token)));
    }

    /** The caller's cart, created (and for guests, the cookie issued) if it does not exist yet. */
    private UUID existingOrNewCart(HttpServletRequest request, HttpServletResponse response) {
        Optional<CustomerPrincipal> customer = CurrentCustomer.get();
        if (customer.isPresent()) {
            return service.getOrCreateForCustomer(customer.get().id());
        }
        Optional<UUID> existing = existingCart(request);
        if (existing.isPresent()) {
            renewGuestCookie(request, response);
            return existing.get();
        }
        // No cookie, a malformed one, or a token that matches nothing (expired, forged): start a fresh cart.
        String token = cookie.newToken();
        UUID cartId = service.createForGuest(GuestCartCookie.hash(token));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.issue(token));
        return cartId;
    }

    /** Slides the guest cart's expiry forward on every write. Signed-in customers have no cart cookie. */
    private void renewGuestCookie(HttpServletRequest request, HttpServletResponse response) {
        if (CurrentCustomer.get().isEmpty()) {
            cookie.readToken(request).ifPresent(token -> response.addHeader(HttpHeaders.SET_COOKIE, cookie.issue(token)));
        }
    }
}
