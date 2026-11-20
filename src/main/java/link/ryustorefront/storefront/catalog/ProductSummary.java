package link.ryustorefront.storefront.catalog;

public record ProductSummary(
        long id,
        String slug,
        String name,
        String brand,
        String gender,
        String categorySlug,
        String categoryName,
        long minPriceMinor,
        String currency,
        boolean inStock,
        /** The first photo, or null when the product has none (the client shows a placeholder). */
        ImageView image) {
}
