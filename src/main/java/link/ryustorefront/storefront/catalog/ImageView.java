package link.ryustorefront.storefront.catalog;

/**
 * One product photo. {@code base} is the object URL without its variant suffix: the served files are
 * {@code base + "-" + width + ".avif"} and {@code ".webp"} for the widths in {@link #WIDTHS}. Width and height are the
 * original's, so the client can reserve the right space (no layout shift).
 */
public record ImageView(String base, int width, int height, String alt) {

    /** The variant widths that scripts/images/variants.mjs writes for every image. */
    public static final int[] WIDTHS = {480, 960, 1440};
}
