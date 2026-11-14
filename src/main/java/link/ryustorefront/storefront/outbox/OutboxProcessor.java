package link.ryustorefront.storefront.outbox;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import link.ryustorefront.storefront.outbox.internal.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers due outbox events to their handlers, one event per transaction.
 *
 * <p>Per event, one transaction does: claim the row ({@code FOR UPDATE SKIP LOCKED}), run the handler,
 * mark it processed. If the handler throws, that transaction rolls back (undoing the handler's
 * database work) and a second, separate step records the failure and schedules a retry. If the
 * process dies, the transaction never commits, the row lock disappears with the connection, and the
 * event is simply claimed again after restart. Several workers can run at once.
 */
@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    /** What one call to {@link #drain} did. */
    public record Drained(int processed, int failed) {
    }

    private enum Step { NONE, DONE, FAILED }

    private final OutboxRepository repository;
    private final TransactionTemplate transaction;
    private final Map<String, OutboxHandler> handlers = new HashMap<>();
    private final int maxAttempts;

    OutboxProcessor(OutboxRepository repository, PlatformTransactionManager transactionManager,
                    ObjectProvider<OutboxHandler> handlerBeans,
                    @Value("${storefront.outbox.max-attempts:10}") int maxAttempts) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        // ObjectProvider, not List: there may legitimately be no handlers at all.
        handlerBeans.stream().forEach(handler -> {
            if (handlers.put(handler.eventType(), handler) != null) {
                throw new IllegalStateException("Two handlers for event type " + handler.eventType());
            }
        });
    }

    /** Handles due events until none are left or {@code maxEvents} were attempted. */
    public Drained drain(int maxEvents) {
        int processed = 0;
        int failed = 0;
        for (int i = 0; i < maxEvents; i++) {
            Step step = processOne();
            if (step == Step.NONE) {
                break;
            }
            if (step == Step.DONE) {
                processed++;
            } else {
                failed++;
            }
        }
        return new Drained(processed, failed);
    }

    private Step processOne() {
        AtomicReference<OutboxEvent> attempted = new AtomicReference<>();
        try {
            return transaction.execute(status -> {
                var claimed = repository.claimNext(maxAttempts);
                if (claimed.isEmpty()) {
                    return Step.NONE;
                }
                OutboxEvent event = claimed.get();
                attempted.set(event);
                OutboxHandler handler = handlers.get(event.eventType());
                if (handler == null) {
                    // Nobody consumes this type (yet). Mark it done and leave a trace rather than retry forever.
                    repository.markProcessed(event.id(), "no handler registered");
                } else {
                    handler.handle(event);
                    repository.markProcessed(event.id(), null);
                }
                return Step.DONE;
            });
        } catch (RuntimeException e) {
            OutboxEvent event = attempted.get();
            if (event == null) {
                throw e; // the claim itself failed (database trouble): not an event's fault
            }
            log.warn("Outbox event {} ({}) failed on attempt {}: {}", event.id(), event.eventType(),
                    event.attempts() + 1, e.toString());
            repository.recordFailure(event.id(), e.toString());
            return Step.FAILED;
        }
    }
}
