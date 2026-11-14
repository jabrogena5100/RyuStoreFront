package link.ryustorefront.storefront.catalog;

import java.util.Locale;

public enum ProductSort {
    RELEVANCE,
    NEWEST,
    PRICE_ASC,
    PRICE_DESC;

    /** Returns null for a missing value so the caller can pick a default; throws for an unknown one. */
    public static ProductSort parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "relevance" -> RELEVANCE;
            case "newest" -> NEWEST;
            case "price_asc" -> PRICE_ASC;
            case "price_desc" -> PRICE_DESC;
            default -> throw new IllegalArgumentException(
                    "Unknown sort '" + value + "'. Use relevance, newest, price_asc or price_desc.");
        };
    }
}
