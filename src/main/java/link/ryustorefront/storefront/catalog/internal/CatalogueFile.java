package link.ryustorefront.storefront.catalog.internal;

import java.util.List;

/**
 * The curated, photographed demo catalogue: classpath:catalogue/catalogue.json, written by scripts/images/fetch.py from
 * content/catalogue/selection.json and the image manifest. Names, brands and copy are invented; every image key has a
 * licence entry in content/catalogue/manifest.json.
 */
public record CatalogueFile(int version, List<Product> products) {

    public record Product(String slug, String name, String brand, String category, String gender, List<String> colours,
                          int priceDollars, String description, List<Image> images) {
    }

    public record Image(String key, int width, int height, String alt) {
    }
}
