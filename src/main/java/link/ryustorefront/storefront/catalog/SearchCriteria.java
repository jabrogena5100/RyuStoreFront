package link.ryustorefront.storefront.catalog;

/**
 * A validated catalog query. Prices are minor units and filter on a product's cheapest active
 * variant. A gender filter also matches unisex products.
 */
public record SearchCriteria(
        String q,
        String category,
        String gender,
        String brand,
        Long minPriceMinor,
        Long maxPriceMinor,
        String sizeLabel,
        boolean inStockOnly,
        ProductSort sort,
        int page,
        int pageSize) {
}
