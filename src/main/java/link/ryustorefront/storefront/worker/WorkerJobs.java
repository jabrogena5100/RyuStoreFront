package link.ryustorefront.storefront.worker;

import link.ryustorefront.storefront.outbox.OutboxProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The background jobs. They run only when {@code storefront.worker.enabled=true}, which the
 * {@code worker} profile sets, so the API process and the tests do not poll unless asked to.
 * One jar, two profiles: the API service runs without it, the worker service with it.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "storefront.worker.enabled", havingValue = "true")
class WorkerJobs {

    private static final Logger log = LoggerFactory.getLogger(WorkerJobs.class);

    private final OutboxProcessor outbox;
    private final ExpirySweep sweep;
    private final int batchSize;

    WorkerJobs(OutboxProcessor outbox, ExpirySweep sweep, @Value("${storefront.worker.batch-size:20}") int batchSize) {
        this.outbox = outbox;
        this.sweep = sweep;
        this.batchSize = batchSize;
        log.info("Worker jobs enabled (outbox batch {})", batchSize);
    }

    @Scheduled(fixedDelayString = "${storefront.worker.poll-interval-ms:1000}")
    void pollOutbox() {
        try {
            OutboxProcessor.Drained drained = outbox.drain(batchSize);
            if (drained.processed() + drained.failed() > 0) {
                log.info("Outbox: {} processed, {} failed", drained.processed(), drained.failed());
            }
        } catch (RuntimeException e) {
            log.error("Outbox poll failed; will retry on the next tick", e);
        }
    }

    @Scheduled(fixedDelayString = "${storefront.worker.sweep-interval-ms:30000}")
    void sweepExpired() {
        try {
            ExpirySweep.Result result = sweep.sweepOnce(batchSize);
            if (result.ordersCancelled() + result.strayReservationsReleased() > 0) {
                log.info("Expiry sweep: {} orders cancelled, {} stray reservations released",
                        result.ordersCancelled(), result.strayReservationsReleased());
            }
        } catch (RuntimeException e) {
            log.error("Expiry sweep failed; will retry on the next tick", e);
        }
    }
}
