package link.ryustorefront.storefront.cart;

import java.util.List;

/** Read access to a signed-in customer's cart for other modules (checkout). */
public interface CustomerCart {

    /** The customer's cart lines in the order they were added; empty if there is no cart or it is empty. */
    List<CartLine> lines(long customerId);
}
