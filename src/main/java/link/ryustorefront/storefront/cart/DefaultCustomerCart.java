package link.ryustorefront.storefront.cart;

import java.util.Collection;
import java.util.List;
import link.ryustorefront.storefront.cart.internal.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultCustomerCart implements CustomerCart {

    private final CartRepository carts;

    DefaultCustomerCart(CartRepository carts) {
        this.carts = carts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartLine> lines(long customerId) {
        return carts.findByCustomer(customerId)
                .map(cartId -> carts.lines(cartId).stream()
                        .map(l -> new CartLine(l.variantId(), l.sku(), l.productName(), l.sizeLabel(), l.colour(),
                                l.unitPriceMinor(), l.currency(), l.quantity(), l.active(), l.stock()))
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void removeLines(long customerId, Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return;
        }
        carts.findByCustomer(customerId).ifPresent(cartId -> carts.removeItems(cartId, variantIds));
    }
}
