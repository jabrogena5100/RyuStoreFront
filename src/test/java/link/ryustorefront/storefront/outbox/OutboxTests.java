package link.ryustorefront.storefront.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.orders.TransitionResult.Outcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox pattern itself: events are written in the caller's transaction, and only then. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxTests {

    @Autowired
    Outbox outbox;
    @Autowired
    Orders orders;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    @AfterAll
    void clean() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE contact_email LIKE 'ob-test-%')").update();
        jdbc.sql("DELETE FROM orders WHERE contact_email LIKE 'ob-test-%'").update();
    }

    private long count(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query(Long.class).single();
    }

    private UUID pendingOrder() {
        return jdbc.sql("""
                INSERT INTO orders (contact_email, status, subtotal_minor, shipping_minor, total_minor, shipping_address)
                VALUES (:e, 'pending', 0, 0, 0, '{}'::jsonb) RETURNING id""")
                .param("e", "ob-test-" + UUID.randomUUID() + "@example.com").query(UUID.class).single();
    }

    @Test
    void publishingOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> outbox.publish("order", "x", "test.record", Map.of("k", "v")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void aCommittedEventIsStoredPendingAndImmediatelyDue() {
        tx().executeWithoutResult(s -> outbox.publish("order", "agg-1", "test.record", Map.of("k", "v")));

        assertThat(count("SELECT count(*) FROM outbox WHERE aggregate_type = 'order' AND aggregate_id = 'agg-1' "
                + "AND event_type = 'test.record' AND payload->>'k' = 'v' AND processed_at IS NULL "
                + "AND attempts = 0 AND available_at <= now()")).isEqualTo(1);
    }

    @Test
    void aRolledBackTransactionLeavesNoEvent() {
        tx().executeWithoutResult(s -> {
            outbox.publish("order", "agg-2", "test.record", Map.of("k", "v"));
            s.setRollbackOnly();
        });

        assertThat(count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void payloadsWithQuotesNewlinesUnicodeAndControlCharactersRoundTrip() {
        Map<String, String> payload = new HashMap<>();
        payload.put("quote", "she said \"hi\"");
        payload.put("backslash", "C:\\temp\\new");
        payload.put("lines", "one\ntwo\r\nthree\tfour");
        payload.put("unicode", "héllo wörld ✓ 日本語 🙂");
        payload.put("control", "bell\u0001end");
        payload.put("empty", "");
        payload.put("key \"with\" odd\nchars", "value");
        payload.put("missing", null);

        tx().executeWithoutResult(s -> outbox.publish("order", "agg-3", "test.record", payload));

        Map<String, String> stored = new HashMap<>();
        jdbc.sql("SELECT e.key, e.value FROM outbox o, jsonb_each_text(o.payload) e WHERE o.aggregate_id = 'agg-3'")
                .query((rs, n) -> {
                    stored.put(rs.getString(1), rs.getString(2));
                    return 0;
                }).list();
        assertThat(stored).isEqualTo(payload);
    }

    @Test
    void aStatusChangeWritesItsEventInTheSameTransaction() {
        UUID id = pendingOrder();

        Outcome outcome = orders.transition(id, OrderStatus.PAID, "paid by test").outcome();

        assertThat(outcome).isEqualTo(Outcome.APPLIED);
        assertThat(count("SELECT count(*) FROM outbox WHERE aggregate_type = 'order' AND aggregate_id = ? "
                + "AND event_type = 'order.paid' AND payload->>'orderId' = ? AND payload->>'from' = 'pending' "
                + "AND payload->>'to' = 'paid' AND payload->>'reason' = 'paid by test'", id.toString(), id.toString()))
                .isEqualTo(1);
    }

    @Test
    void illegalAndRepeatedStatusChangesWriteNoEvent() {
        UUID id = pendingOrder();
        orders.transition(id, OrderStatus.PAID, "first");

        Outcome repeat = orders.transition(id, OrderStatus.PAID, "again").outcome();
        Outcome illegal = orders.transition(id, OrderStatus.CANCELLED, "too late").outcome();

        assertThat(repeat).isEqualTo(Outcome.ALREADY);
        assertThat(illegal).isEqualTo(Outcome.ILLEGAL);
        assertThat(count("SELECT count(*) FROM outbox WHERE aggregate_id = ?", id.toString())).isEqualTo(1);
    }

    @Test
    void aStatusChangeRolledBackWithItsTransactionLeavesNoEventAndNoNewStatus() {
        UUID id = pendingOrder();

        tx().executeWithoutResult(s -> {
            assertThat(orders.transition(id, OrderStatus.PAID, "will roll back").applied()).isTrue();
            s.setRollbackOnly();
        });

        assertThat(count("SELECT count(*) FROM outbox WHERE aggregate_id = ?", id.toString())).isZero();
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", id).query(String.class).single())
                .isEqualTo("pending");
        assertThat(count("SELECT count(*) FROM order_status_history WHERE order_id = ?::uuid", id.toString())).isZero();
    }
}
