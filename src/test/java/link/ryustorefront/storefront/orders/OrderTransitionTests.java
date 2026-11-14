package link.ryustorefront.storefront.orders;

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
import link.ryustorefront.storefront.orders.TransitionResult.Outcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The real {@link Orders#transition} against a real Postgres: all 36 status pairs through the service
 * (not just the rule table), with independent SQL for status, history and version, then concurrent
 * callers on one order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderTransitionTests {

    private static final String EMAIL = "sm-test-%s@example.com";

    @Autowired
    Orders orders;
    @Autowired
    JdbcClient jdbc;

    @AfterAll
    void cleanUp() {
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE contact_email LIKE 'sm-test-%')").update();
        jdbc.sql("DELETE FROM orders WHERE contact_email LIKE 'sm-test-%'").update();
    }

    /** An order inserted directly in the given status, so every starting state can be tested. */
    private UUID order(OrderStatus status) {
        return jdbc.sql("""
                INSERT INTO orders (contact_email, status, subtotal_minor, shipping_minor, total_minor,
                                    shipping_address, expires_at)
                VALUES (:email, :status, 0, 0, 0, '{}'::jsonb,
                        CASE WHEN :status = 'pending' THEN now() + interval '30 minutes' END)
                RETURNING id""")
                .param("email", EMAIL.formatted(UUID.randomUUID())).param("status", status.dbValue())
                .query(UUID.class).single();
    }

    private String statusOf(UUID id) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", id).query(String.class).single();
    }

    private long versionOf(UUID id) {
        return jdbc.sql("SELECT version FROM orders WHERE id = :id").param("id", id).query(Long.class).single();
    }

    private long historyRows(UUID id) {
        return jdbc.sql("SELECT count(*) FROM order_status_history WHERE order_id = :id").param("id", id)
                .query(Long.class).single();
    }

    @Test
    void allThirtySixPairsBehaveAsTheTableSaysThroughTheRealService() {
        int checked = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                checked++;
                UUID id = order(from);
                String pair = from + ">" + to;

                TransitionResult result = orders.transition(id, to, "table test");

                Outcome expected = from == to ? Outcome.ALREADY
                        : OrderStatusTests.LEGAL.contains(pair) ? Outcome.APPLIED : Outcome.ILLEGAL;
                assertThat(result.outcome()).as(pair).isEqualTo(expected);
                assertThat(result.previous()).as(pair).isEqualTo(from);
                if (expected == Outcome.APPLIED) {
                    assertThat(statusOf(id)).as(pair).isEqualTo(to.dbValue());
                    assertThat(versionOf(id)).as(pair).isEqualTo(1);
                    assertThat(historyRows(id)).as(pair).isEqualTo(1);
                    assertThat(jdbc.sql("SELECT from_status = :f AND to_status = :t AND reason = 'table test' "
                                    + "FROM order_status_history WHERE order_id = :id")
                            .param("f", from.dbValue()).param("t", to.dbValue()).param("id", id)
                            .query(Boolean.class).single()).as(pair).isTrue();
                } else {
                    assertThat(statusOf(id)).as(pair + " must not change").isEqualTo(from.dbValue());
                    assertThat(versionOf(id)).as(pair).isZero();
                    assertThat(historyRows(id)).as(pair + " must write no history").isZero();
                }
            }
        }
        assertThat(checked).isEqualTo(36);
    }

    @Test
    void anUnknownOrderIsNotFoundNotAnError() {
        TransitionResult result = orders.transition(UUID.randomUUID(), OrderStatus.PAID, "x");

        assertThat(result.outcome()).isEqualTo(Outcome.NOT_FOUND);
        assertThat(result.previous()).isNull();
    }

    @Test
    void repeatingATransitionIsAHarmlessNoOp() {
        UUID id = order(OrderStatus.PENDING);

        TransitionResult first = orders.transition(id, OrderStatus.PAID, "payment received");
        TransitionResult second = orders.transition(id, OrderStatus.PAID, "payment received again");
        TransitionResult third = orders.transition(id, OrderStatus.PAID, "and again");

        assertThat(first.outcome()).isEqualTo(Outcome.APPLIED);
        assertThat(second.outcome()).isEqualTo(Outcome.ALREADY);
        assertThat(third.reachedTarget()).isTrue();
        assertThat(third.applied()).isFalse();
        assertThat(historyRows(id)).as("one history row, not three").isEqualTo(1);
        assertThat(versionOf(id)).isEqualTo(1);
    }

    @Test
    void leavingPendingClearsTheHoldExpiry() {
        UUID id = order(OrderStatus.PENDING);
        assertThat(jdbc.sql("SELECT expires_at IS NOT NULL FROM orders WHERE id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();

        orders.transition(id, OrderStatus.PAID, "paid");

        assertThat(jdbc.sql("SELECT expires_at IS NULL FROM orders WHERE id = :id").param("id", id)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void aFullLifecycleWritesOneOrderedHistoryRowPerStep() {
        UUID id = order(OrderStatus.PENDING);

        orders.transition(id, OrderStatus.PAID, "paid");
        orders.transition(id, OrderStatus.FULFILLED, "packed");
        orders.transition(id, OrderStatus.SHIPPED, "handed to carrier");
        orders.transition(id, OrderStatus.REFUNDED, "returned");

        List<String> steps = jdbc.sql("SELECT from_status || '>' || to_status FROM order_status_history "
                        + "WHERE order_id = :id ORDER BY id").param("id", id).query(String.class).list();
        assertThat(steps).containsExactly("pending>paid", "paid>fulfilled", "fulfilled>shipped", "shipped>refunded");
        assertThat(statusOf(id)).isEqualTo("refunded");
        assertThat(versionOf(id)).isEqualTo(4);
        assertThat(orders.transition(id, OrderStatus.PAID, "late").outcome()).as("terminal").isEqualTo(Outcome.ILLEGAL);
    }

    @Test
    void cancelPendingCancelsOnlyPendingOrders() {
        UUID pending = order(OrderStatus.PENDING);
        UUID paid = order(OrderStatus.PAID);

        assertThat(orders.cancelPending(pending, "superseded")).isTrue();
        assertThat(orders.cancelPending(paid, "superseded")).as("a paid order is never cancelled").isFalse();
        assertThat(orders.cancelPending(pending, "again")).as("already cancelled").isFalse();

        assertThat(statusOf(pending)).isEqualTo("cancelled");
        assertThat(statusOf(paid)).isEqualTo("paid");
    }

    @Test
    void tenConcurrentCallsForTheSameTransitionApplyItExactlyOnce() throws Exception {
        UUID id = order(OrderStatus.PENDING);

        List<Outcome> outcomes = race(List.of(
                OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID,
                OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID, OrderStatus.PAID), id);

        assertThat(outcomes.stream().filter(o -> o == Outcome.APPLIED).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o == Outcome.ALREADY).count()).isEqualTo(9);
        assertThat(historyRows(id)).isEqualTo(1);
        assertThat(versionOf(id)).isEqualTo(1);
        assertThat(statusOf(id)).isEqualTo("paid");
    }

    @Test
    void aPaymentRacingACancellationHasExactlyOneWinnerInEveryRound() throws Exception {
        for (int round = 1; round <= 20; round++) {
            UUID id = order(OrderStatus.PENDING);

            List<Outcome> outcomes = race(List.of(OrderStatus.PAID, OrderStatus.CANCELLED), id);

            assertThat(outcomes).as("round %d", round).containsExactlyInAnyOrder(Outcome.APPLIED, Outcome.ILLEGAL);
            assertThat(historyRows(id)).as("round %d", round).isEqualTo(1);
            assertThat(versionOf(id)).as("round %d", round).isEqualTo(1);
            String winner = jdbc.sql("SELECT to_status FROM order_status_history WHERE order_id = :id")
                    .param("id", id).query(String.class).single();
            assertThat(statusOf(id)).as("round %d: the stored status is the winner's", round).isEqualTo(winner);
        }
    }

    /** Releases one thread per target at the same instant and collects what each was told. */
    private List<Outcome> race(List<OrderStatus> targets, UUID id) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(targets.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (OrderStatus target : targets) {
            futures.add(pool.submit(() -> {
                go.await();
                return orders.transition(id, target, "race").outcome();
            }));
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }
}
