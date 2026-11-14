package link.ryustorefront.storefront.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order as the API shows it. Items are the snapshot taken at checkout, not live catalogue data. */
public record OrderSummary(
        UUID id,
        String status,
        String contactEmail,
        String currency,
        long subtotalMinor,
        long shippingMinor,
        long totalMinor,
        Instant expiresAt,
        Instant createdAt,
        List<Line> items) {

    public record Line(String sku, String productName, String sizeLabel, String colour,
                       long unitPriceMinor, int quantity, long lineTotalMinor) {
    }
}
