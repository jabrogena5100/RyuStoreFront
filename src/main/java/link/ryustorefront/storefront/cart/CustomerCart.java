package link.ryustorefront.storefront.cart;

import java.util.Collection;
import java.util.List;

/** A signed-in customer's cart as other modules need it: read by checkout, emptied of purchased lines after payment. */
public interface CustomerCart {

    /** The customer's cart lines in the order they were added; empty if there is no cart or it is empty. */
    List<CartLine> lines(long customerId);

    /**
     * Removes the given variants from the customer's cart (what they just bought). Idempotent: lines that are
     * not there are ignored, and so is a customer with no cart. Lines for other variants stay.
     */
    void removeLines(long customerId, Collection<Long> variantIds);
}
