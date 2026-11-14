package link.ryustorefront.storefront.outbox.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import link.ryustorefront.storefront.outbox.OutboxEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL over the outbox table (V4). */
@Repository
public class OutboxRepository {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String aggregateType, String aggregateId, String eventType, String payloadJson) {
        jdbc.sql("INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES (:type, :id, :event, CAST(:payload AS jsonb))")
                .param("type", aggregateType).param("id", aggregateId).param("event", eventType)
                .param("payload", payloadJson).update();
    }

    /**
     * Locks and returns the next due event, or empty. {@code SKIP LOCKED} means workers never wait for
     * each other and never take the same event; the lock lasts until the surrounding transaction ends.
     * Events at or past {@code maxAttempts} are dead: never claimed again, kept for inspection.
     */
    public Optional<OutboxEvent> claimNext(int maxAttempts) {
        Optional<OutboxEvent> claimed = jdbc.sql("""
                SELECT id, aggregate_type, aggregate_id, event_type, attempts FROM outbox
                WHERE processed_at IS NULL AND available_at <= now() AND attempts < :max
                ORDER BY available_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED""")
                .param("max", maxAttempts)
                .query((rs, n) -> new OutboxEvent(rs.getLong("id"), rs.getString("aggregate_type"),
                        rs.getString("aggregate_id"), rs.getString("event_type"), Map.of(), rs.getInt("attempts")))
                .optional();
        return claimed.map(e -> new OutboxEvent(e.id(), e.aggregateType(), e.aggregateId(), e.eventType(),
                payload(e.id()), e.attempts()));
    }

    private Map<String, String> payload(long id) {
        List<String[]> pairs = jdbc.sql("SELECT e.key, e.value FROM outbox o, jsonb_each_text(o.payload) e WHERE o.id = :id")
                .param("id", id).query((rs, n) -> new String[] {rs.getString(1), rs.getString(2)}).list();
        Map<String, String> payload = new HashMap<>();
        pairs.forEach(p -> payload.put(p[0], p[1]));
        return payload;
    }

    public void markProcessed(long id, String note) {
        jdbc.sql("UPDATE outbox SET processed_at = now(), last_error = :note WHERE id = :id")
                .param("note", note, java.sql.Types.VARCHAR).param("id", id).update();
    }

    /**
     * Counts a failed attempt and delays the next one: 5 s, 10 s, 20 s, ... capped at 15 minutes. Runs in
     * its own transaction after the failed attempt rolled back, and does nothing if the event was
     * meanwhile processed.
     */
    public void recordFailure(long id, String error) {
        String trimmed = error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        jdbc.sql("""
                UPDATE outbox
                SET attempts = attempts + 1,
                    last_error = :error,
                    available_at = now() + make_interval(secs => least(5 * power(2, attempts), 900))
                WHERE id = :id AND processed_at IS NULL""")
                .param("error", trimmed).param("id", id).update();
    }
}
