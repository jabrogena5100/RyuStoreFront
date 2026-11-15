package link.ryustorefront.storefront.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An order as its owner sees it on the order and confirmation pages: the checkout snapshot plus a link and brand for each
 * item (read from the variant's product) and the status history. {@code expiresAt} is null once the order leaves pending.
 */
public record OrderDetail(
        UUID id,
        String status,
        String contactEmail,
        String currency,
        long subtotalMinor,
        long shippingMinor,
        long totalMinor,
        ShippingAddress shippingAddress,
        Instant expiresAt,
        Instant createdAt,
        List<Item> items,
        List<StatusChange> history) {

    public record Item(String sku, String productName, String productSlug, String brand, String sizeLabel, String colour,
                       long unitPriceMinor, int quantity, long lineTotalMinor) {
    }

    /** One row of order_status_history, oldest first. */
    public record StatusChange(String status, Instant at) {
    }
}
