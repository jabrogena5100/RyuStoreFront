package link.ryustorefront.storefront.inventory;

import java.time.Instant;
import java.util.UUID;

public record Reservation(UUID id, long variantId, int quantity, Instant expiresAt) {
}
