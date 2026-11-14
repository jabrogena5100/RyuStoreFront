package link.ryustorefront.storefront.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request and response shapes of {@code /api/cart}. Documented in docs/api/cart.md. */
public final class CartDtos {

    private CartDtos() {
    }

    /** Quantity 0 removes the line. The upper bound mirrors the database CHECK (1 to 10 per line). */
    public record SetQuantityRequest(@NotNull @Min(0) @Max(CartService.MAX_QUANTITY) Integer quantity) {
    }

    /**
     * One cart line with live catalogue data. {@code available} is false when the variant was
     * deactivated or stock fell below the quantity; {@code maxQuantity} is how many the shopper
     * could currently set (never above 10, 0 when unavailable).
     */
    public record LineView(
            String sku,
            String productSlug,
            String productName,
            String brand,
            String sizeLabel,
            String colour,
            long unitPriceMinor,
            String currency,
            int quantity,
            long lineTotalMinor,
            boolean available,
            int maxQuantity) {
    }

    /** {@code subtotalMinor} counts every line, including unavailable ones; checkout is where availability is enforced. */
    public record CartView(int itemCount, long subtotalMinor, String currency, List<LineView> lines) {

        static CartView empty() {
            return new CartView(0, 0, CartService.CURRENCY, List.of());
        }
    }
}
