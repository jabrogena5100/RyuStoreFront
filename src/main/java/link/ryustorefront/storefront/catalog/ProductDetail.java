package link.ryustorefront.storefront.catalog;

import java.util.List;

/** Variants expose availability as a boolean only; exact stock counts are not public. */
public record ProductDetail(
        long id,
        String slug,
        String name,
        String brand,
        String description,
        String gender,
        String categorySlug,
        String categoryName,
        List<VariantView> variants) {

    public record VariantView(
            String sku,
            String sizeLabel,
            String colour,
            long priceMinor,
            String currency,
            boolean available) {
    }
}
