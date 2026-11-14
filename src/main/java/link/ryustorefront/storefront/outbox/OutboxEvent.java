package link.ryustorefront.storefront.outbox;

import java.util.Map;

/** An event as a handler sees it. {@code attempts} is how many earlier tries failed. */
public record OutboxEvent(long id, String aggregateType, String aggregateId, String eventType,
                          Map<String, String> payload, int attempts) {
}
