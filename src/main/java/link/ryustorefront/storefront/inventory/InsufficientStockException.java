package link.ryustorefront.storefront.inventory;

/** Thrown when a variant has fewer units than requested (or is inactive). Rolls the reservation back. */
public class InsufficientStockException extends RuntimeException {

    private final long variantId;
    private final int requested;

    public InsufficientStockException(long variantId, int requested) {
        super("Not enough stock for variant " + variantId + " (requested " + requested + ")");
        this.variantId = variantId;
        this.requested = requested;
    }

    public long variantId() {
        return variantId;
    }

    public int requested() {
        return requested;
    }
}
