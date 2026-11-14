package link.ryustorefront.storefront.cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import link.ryustorefront.storefront.cart.CartDtos.CartView;
import link.ryustorefront.storefront.cart.CartDtos.LineView;
import link.ryustorefront.storefront.cart.internal.CartRepository;
import link.ryustorefront.storefront.cart.internal.CartRepository.Variant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cart operations. Carts are advisory: they never reserve stock (checkout does, in slice 3d). */
@Service
class CartService {

    static final int MAX_QUANTITY = 10;
    /** v1 sells in one currency; mixed-currency carts are out of scope. */
    static final String CURRENCY = "USD";

    private final CartRepository carts;

    CartService(CartRepository carts) {
        this.carts = carts;
    }

    Optional<UUID> findForCustomer(long customerId) {
        return carts.findByCustomer(customerId);
    }

    Optional<UUID> findForGuest(String tokenHash) {
        return carts.findByGuestHash(tokenHash);
    }

    UUID getOrCreateForCustomer(long customerId) {
        return carts.getOrCreateForCustomer(customerId);
    }

    UUID createForGuest(String tokenHash) {
        return carts.createForGuest(tokenHash);
    }

    Optional<Variant> findVariant(String sku) {
        return carts.findVariant(sku);
    }

    @Transactional
    void setQuantity(UUID cartId, long variantId, int quantity) {
        carts.setQuantity(cartId, variantId, quantity);
    }

    @Transactional
    void remove(UUID cartId, long variantId) {
        carts.removeItem(cartId, variantId);
    }

    @Transactional(readOnly = true)
    CartView view(UUID cartId) {
        List<CartRepository.Line> lines = carts.lines(cartId);
        List<LineView> views = lines.stream().map(CartService::toView).toList();
        int items = views.stream().mapToInt(LineView::quantity).sum();
        long subtotal = views.stream().mapToLong(LineView::lineTotalMinor).reduce(0L, Math::addExact);
        return new CartView(items, subtotal, lines.isEmpty() ? CURRENCY : lines.get(0).currency(), views);
    }

    /**
     * Moves a guest cart's lines into the customer's cart (creating it if needed) and deletes the guest
     * cart, in one transaction. On a shared variant the larger quantity wins, so repeating a merge or
     * merging the same items twice never inflates a line.
     *
     * @return true if a guest cart was merged, false if the token matched no cart
     */
    @Transactional
    boolean mergeGuestIntoCustomer(String guestTokenHash, long customerId) {
        Optional<UUID> guest = carts.findByGuestHash(guestTokenHash);
        if (guest.isEmpty() || !carts.lock(guest.get())) {
            return false; // never existed, or a concurrent login already merged it
        }
        UUID customer = carts.getOrCreateForCustomer(customerId);
        carts.mergeInto(guest.get(), customer);
        carts.delete(guest.get());
        return true;
    }

    private static LineView toView(CartRepository.Line line) {
        boolean available = line.active() && line.stock() >= line.quantity();
        int maxQuantity = line.active() ? Math.min(MAX_QUANTITY, line.stock()) : 0;
        return new LineView(line.sku(), line.productSlug(), line.productName(), line.brand(), line.sizeLabel(),
                line.colour(), line.unitPriceMinor(), line.currency(), line.quantity(),
                Math.multiplyExact(line.unitPriceMinor(), (long) line.quantity()), available, maxQuantity);
    }
}
