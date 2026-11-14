package link.ryustorefront.storefront.outbox;

import java.util.Map;
import link.ryustorefront.storefront.outbox.internal.Json;
import link.ryustorefront.storefront.outbox.internal.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records an event to be delivered later. This is the "transactional outbox": the event row is written
 * in the same transaction as the change that caused it, so the two commit or roll back together and a
 * committed change can never lose its event.
 */
@Service
public class Outbox {

    private final OutboxRepository repository;

    Outbox(OutboxRepository repository) {
        this.repository = repository;
    }

    /**
     * Adds an event to the outbox.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException if there is no
     *         transaction: publishing outside the transaction of the change defeats the pattern
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, String aggregateId, String eventType, Map<String, String> payload) {
        repository.insert(aggregateType, aggregateId, eventType, Json.object(payload));
    }
}
