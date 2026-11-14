package link.ryustorefront.storefront.cart;

/**
 * One line of a customer's cart as checkout needs it: live catalogue data, read at the moment of the
 * call. {@code active} means the variant and its product are both on sale; {@code stock} is the units
 * currently unreserved (an in-module detail that is never shown to shoppers).
 */
public record CartLine(long variantId, String sku, String productName, String sizeLabel, String colour,
                       long unitPriceMinor, String currency, int quantity, boolean active, int stock) {
}
