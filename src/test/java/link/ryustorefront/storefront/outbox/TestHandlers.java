package link.ryustorefront.storefront.outbox;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Event handlers that exist only on the test classpath. Behaviour is driven by the event payload, so
 * the handlers hold no configuration: {@code failTimes} makes the first N attempts throw (compared with
 * the event's recorded attempts), {@code effect=true} writes a database row before any failure so
 * tests can prove a failed attempt's work is rolled back, and {@code sleepMs} slows a handler down.
 */
@Component
class RecordingHandler implements OutboxHandler {

    /** How many times each event id reached the handler (every attempt, successful or not). */
    static final Map<Long, Integer> CALLS = new ConcurrentHashMap<>();
    /** Event ids in the order they were handled successfully. */
    static final List<Long> HANDLED = new CopyOnWriteArrayList<>();

    private final JdbcClient jdbc;

    RecordingHandler(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    static void reset() {
        CALLS.clear();
        HANDLED.clear();
    }

    @Override
    public String eventType() {
        return "test.record";
    }

    @Override
    public void handle(OutboxEvent event) {
        CALLS.merge(event.id(), 1, Integer::sum);
        Map<String, String> payload = event.payload();
        if ("true".equals(payload.get("effect"))) {
            jdbc.sql("INSERT INTO outbox_test_effects (event_id) VALUES (:id)").param("id", event.id()).update();
        }
        String sleep = payload.get("sleepMs");
        if (sleep != null) {
            try {
                Thread.sleep(Long.parseLong(sleep));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        int failTimes = Integer.parseInt(payload.getOrDefault("failTimes", "0"));
        if (event.attempts() < failTimes) {
            throw new IllegalStateException("failing on purpose (attempt " + (event.attempts() + 1) + ")");
        }
        HANDLED.add(event.id());
    }
}
