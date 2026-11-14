package link.ryustorefront.storefront.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.outbox.OutboxProcessor.Drained;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The real processor against a real Postgres, with test handlers that fail or sleep on demand.
 * Timing is checked from the database's own clock, and "time passing" is simulated by moving
 * {@code available_at}, so no test sleeps for a backoff.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxProcessorTests {

    @Autowired
    OutboxProcessor processor;
    @Autowired
    JdbcClient jdbc;

    @BeforeAll
    void createScratchTable() {
        jdbc.sql("CREATE TABLE IF NOT EXISTS outbox_test_effects (event_id bigint NOT NULL)").update();
    }

    @AfterAll
    void dropScratchTable() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DROP TABLE IF EXISTS outbox_test_effects").update();
    }

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM outbox_test_effects").update();
        RecordingHandler.reset();
        MultiFirstHandler.RAN.clear();
    }

    /** Inserts a due event directly and returns its id. */
    private long event(String type, String payloadJson) {
        return jdbc.sql("INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES ('test', :agg, :type, CAST(:p AS jsonb)) RETURNING id")
                .param("agg", UUID.randomUUID().toString()).param("type", type).param("p", payloadJson)
                .query(Long.class).single();
    }

    private boolean processed(long id) {
        return jdbc.sql("SELECT processed_at IS NOT NULL FROM outbox WHERE id = :id").param("id", id)
                .query(Boolean.class).single();
    }

    private int attempts(long id) {
        return jdbc.sql("SELECT attempts FROM outbox WHERE id = :id").param("id", id).query(Integer.class).single();
    }

    private String lastError(long id) {
        return jdbc.sql("SELECT last_error FROM outbox WHERE id = :id").param("id", id).query(String.class).optional()
                .orElse(null);
    }

    private double secondsUntilDue(long id) {
        return jdbc.sql("SELECT EXTRACT(EPOCH FROM (available_at - now())) FROM outbox WHERE id = :id")
                .param("id", id).query(Double.class).single();
    }

    private void makeDueNow(long id) {
        jdbc.sql("UPDATE outbox SET available_at = now() WHERE id = :id").param("id", id).update();
    }

    private long effectRows() {
        return jdbc.sql("SELECT count(*) FROM outbox_test_effects").query(Long.class).single();
    }

    @Test
    void aSuccessfulHandlerMarksTheEventProcessedOnce() {
        long id = event("test.record", "{}");

        Drained first = processor.drain(10);
        Drained second = processor.drain(10);

        assertThat(first).isEqualTo(new Drained(1, 0));
        assertThat(second).isEqualTo(new Drained(0, 0));
        assertThat(processed(id)).isTrue();
        assertThat(attempts(id)).isZero();
        assertThat(lastError(id)).isNull();
        assertThat(RecordingHandler.CALLS).containsEntry(id, 1);
    }

    @Test
    void aFailingEventIsRetriedWithGrowingBackoffAndItsDatabaseWorkRollsBack() {
        long id = event("test.record", "{\"failTimes\":\"2\",\"effect\":\"true\"}");

        Drained firstTry = processor.drain(10);
        assertThat(firstTry).isEqualTo(new Drained(0, 1));
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(processed(id)).isFalse();
        assertThat(lastError(id)).contains("failing on purpose");
        assertThat(secondsUntilDue(id)).as("first retry in about 5 s").isBetween(3.0, 7.0);
        assertThat(effectRows()).as("the failed attempt's own write was rolled back").isZero();

        assertThat(processor.drain(10)).as("not due yet").isEqualTo(new Drained(0, 0));

        makeDueNow(id);
        assertThat(processor.drain(10)).isEqualTo(new Drained(0, 1));
        assertThat(attempts(id)).isEqualTo(2);
        assertThat(secondsUntilDue(id)).as("second retry in about 10 s").isBetween(8.0, 12.0);
        assertThat(effectRows()).isZero();

        makeDueNow(id);
        assertThat(processor.drain(10)).isEqualTo(new Drained(1, 0));
        assertThat(processed(id)).isTrue();
        assertThat(lastError(id)).as("success clears the old error").isNull();
        assertThat(effectRows()).as("only the successful attempt's write is kept").isEqualTo(1);
        assertThat(RecordingHandler.CALLS).containsEntry(id, 3);
    }

    @Test
    void anEventThatKeepsFailingIsDeadAtTheAttemptLimitAndNeverClaimedAgain() {
        long id = event("test.record", "{\"failTimes\":\"999\"}");
        jdbc.sql("UPDATE outbox SET attempts = 9 WHERE id = :id").param("id", id).update(); // limit is 10

        assertThat(processor.drain(10)).isEqualTo(new Drained(0, 1));
        assertThat(attempts(id)).isEqualTo(10);

        makeDueNow(id);
        assertThat(processor.drain(10)).as("dead events are left alone").isEqualTo(new Drained(0, 0));
        assertThat(processed(id)).as("kept for inspection, not silently dropped").isFalse();
        assertThat(attempts(id)).isEqualTo(10);
        assertThat(RecordingHandler.CALLS).containsEntry(id, 1);
    }

    @Test
    void anEventNotYetDueIsNotClaimed() {
        long id = event("test.record", "{}");
        jdbc.sql("UPDATE outbox SET available_at = now() + interval '1 hour' WHERE id = :id").param("id", id).update();

        assertThat(processor.drain(10)).isEqualTo(new Drained(0, 0));
        assertThat(RecordingHandler.CALLS).doesNotContainKey(id);
    }

    @Test
    void anEventWithNoHandlerIsMarkedDoneWithATraceInsteadOfRetriedForever() {
        long id = event("nobody.listens", "{}");

        assertThat(processor.drain(10)).isEqualTo(new Drained(1, 0));

        assertThat(processed(id)).isTrue();
        assertThat(lastError(id)).isEqualTo("no handler registered");
    }

    @Test
    void eventsAreHandledOldestFirst() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(event("test.record", "{}"));
        }

        assertThat(processor.drain(10)).isEqualTo(new Drained(5, 0));

        assertThat(RecordingHandler.HANDLED).containsExactlyElementsOf(ids);
    }

    @Test
    void drainStopsAtTheRequestedBatchSize() {
        for (int i = 0; i < 5; i++) {
            event("test.record", "{}");
        }

        assertThat(processor.drain(2)).isEqualTo(new Drained(2, 0));
        assertThat(processor.drain(10)).isEqualTo(new Drained(3, 0));
    }

    @Test
    void fourConcurrentWorkersHandleEverySixtyEventsExactlyOnce() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            ids.add(event("test.record", "{\"sleepMs\":\"5\"}"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Drained>> results = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            results.add(pool.submit(() -> {
                go.await();
                return processor.drain(100);
            }));
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        int total = 0;
        int workersThatWorked = 0;
        for (Future<Drained> result : results) {
            Drained drained = result.get();
            assertThat(drained.failed()).isZero();
            total += drained.processed();
            if (drained.processed() > 0) {
                workersThatWorked++;
            }
        }
        assertThat(total).isEqualTo(60);
        assertThat(workersThatWorked).as("the work really was shared").isGreaterThan(1);
        assertThat(RecordingHandler.CALLS).hasSize(60);
        assertThat(RecordingHandler.CALLS.values()).allMatch(calls -> calls == 1);
        assertThat(RecordingHandler.CALLS.keySet()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE processed_at IS NULL").query(Long.class).single()).isZero();
    }

    @Test
    void everyHandlerOfAnEventTypeRunsInOrderWithinOneEventAndTheEventIsProcessedOnce() {
        long id = event("test.multi", "{}");

        assertThat(processor.drain(10)).isEqualTo(new Drained(1, 0));

        assertThat(MultiFirstHandler.RAN).containsExactly("first", "second");
        assertThat(effectRows()).isEqualTo(1);
        assertThat(processed(id)).isTrue();
    }

    @Test
    void ifALaterHandlerFailsTheEarlierHandlersWorkRollsBackAndTheWholeEventIsRetried() {
        long id = event("test.multi", "{\"failTimes\":\"1\"}");

        assertThat(processor.drain(10)).isEqualTo(new Drained(0, 1));
        assertThat(MultiFirstHandler.RAN).containsExactly("first", "second");
        assertThat(effectRows()).as("the first handler's row was rolled back with the failed attempt").isZero();
        assertThat(attempts(id)).isEqualTo(1);
        assertThat(processed(id)).isFalse();

        makeDueNow(id);
        assertThat(processor.drain(10)).isEqualTo(new Drained(1, 0));
        assertThat(MultiFirstHandler.RAN).containsExactly("first", "second", "first", "second");
        assertThat(effectRows()).isEqualTo(1);
        assertThat(processed(id)).isTrue();
    }
}
