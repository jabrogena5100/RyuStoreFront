package link.ryustorefront.storefront.catalog;

/**
 * A validated catalog query. Prices are minor units and filter on a product's cheapest active
 * variant. A gender filter also matches unisex products. A category matches its child categories too unless
 * {@code categoryExact} is set.
 */
public record SearchCriteria(
        String q,
        String category,
        boolean categoryExact,
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
