package link.ryustorefront.storefront.orders;

import java.util.List;

/** Everything needed to create a pending order. Prices are the snapshot taken at checkout. */
public record NewOrder(
        long customerId,
        String contactEmail,
        String currency,
        long subtotalMinor,
        long shippingMinor,
        ShippingAddress address,
        String idempotencyKey,
        List<Item> items) {

    public record Item(long variantId, String sku, String productName, String sizeLabel, String colour,
                       long unitPriceMinor, int quantity) {
    }
}
