package link.ryustorefront.storefront.worker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.inventory.StockReservations;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.Orders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Abandoned checkouts are given up on: the pending order is cancelled and its stock returned, once,
 * however many sweeps run. Real reservations, real orders, real Postgres; time is simulated by moving
 * expiry timestamps into the past.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpirySweepTests {

    @Autowired
    ExpirySweep sweep;
    @Autowired
    StockReservations stock;
    @Autowired
    Orders orders;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearOutbox() {
        jdbc.sql("DELETE FROM outbox").update();
    }

    @AfterAll
    void cleanUp() {
        jdbc.sql("DELETE FROM outbox").update();
        String mine = "SELECT id FROM orders WHERE contact_email LIKE 'sweep-test-%'";
        jdbc.sql("DELETE FROM reservations WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM reservations WHERE variant_id IN (SELECT id FROM variants WHERE sku LIKE 'SWEEPTEST-%')").update();
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE contact_email LIKE 'sweep-test-%'").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'SWEEPTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'sweep-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'sweep-test-c-%'").update();
    }

    private long variant(int stockUnits) {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'Test') RETURNING id")
                .param("s", "sweep-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("INSERT INTO products (slug, name, brand, category_id, gender) "
                        + "VALUES (:s, 'Sweep test', 'Test', :c, 'unisex') RETURNING id")
                .param("s", "sweep-test-p-" + id).param("c", category).query(Long.class).single();
        return jdbc.sql("INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock) "
                        + "VALUES (:p, :sku, '42', 'black', 1000, :stock) RETURNING id")
                .param("p", product).param("sku", "SWEEPTEST-" + id).param("stock", stockUnits)
                .query(Long.class).single();
    }

    /** A pending order holding {@code quantity} units of the variant, as checkout would leave it. */
    private UUID heldOrder(long variantId, int quantity) {
        UUID id = jdbc.sql("""
                INSERT INTO orders (contact_email, status, subtotal_minor, shipping_minor, total_minor,
                                    shipping_address, expires_at)
                VALUES (:e, 'pending', 0, 0, 0, '{}'::jsonb, now() + interval '30 minutes') RETURNING id""")
                .param("e", "sweep-test-" + UUID.randomUUID() + "@example.com").query(UUID.class).single();
        stock.reserve(id, Map.of(variantId, quantity), Duration.ofMinutes(30));
        return id;
    }

    private void letTheHoldRunOut(UUID orderId) {
        jdbc.sql("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = :id").param("id", orderId).update();
        jdbc.sql("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE order_id = :id")
                .param("id", orderId).update();
    }

    private int stockOf(long variantId) {
        return jdbc.sql("SELECT stock FROM variants WHERE id = :id").param("id", variantId).query(Integer.class).single();
    }

    private String statusOf(UUID orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId).query(String.class).single();
    }

    @Test
    void anOverdueOrderIsCancelledItsStockReturnedAndAnEventQueued() {
        long variant = variant(10);
        UUID order = heldOrder(variant, 3);
        assertThat(stockOf(variant)).isEqualTo(7);
        letTheHoldRunOut(order);

        ExpirySweep.Result result = sweep.sweepOnce(50);

        assertThat(result.ordersCancelled()).isEqualTo(1);
        assertThat(statusOf(order)).isEqualTo("cancelled");
        assertThat(stockOf(variant)).as("stock is back").isEqualTo(10);
        assertThat(jdbc.sql("SELECT status FROM reservations WHERE order_id = :id").param("id", order)
                .query(String.class).single()).isEqualTo("released");
        assertThat(jdbc.sql("SELECT reason FROM order_status_history WHERE order_id = :id AND to_status = 'cancelled'")
                .param("id", order).query(String.class).single()).isEqualTo("payment window expired");
        assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE aggregate_id = :id AND event_type = 'order.cancelled'")
                .param("id", order.toString()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void ordersStillInsideTheirHoldAndPaidOrdersAreLeftAlone() {
        long variant = variant(10);
        UUID stillHeld = heldOrder(variant, 2);
        UUID paid = heldOrder(variant, 3);
        stock.commit(paid);
        assertThat(orders.transition(paid, OrderStatus.PAID, "paid").applied()).isTrue();
        assertThat(stockOf(variant)).isEqualTo(5);

        ExpirySweep.Result result = sweep.sweepOnce(50);

        assertThat(result.ordersCancelled()).isZero();
        assertThat(statusOf(stillHeld)).isEqualTo("pending");
        assertThat(statusOf(paid)).isEqualTo("paid");
        assertThat(stockOf(variant)).as("nothing was given back").isEqualTo(5);
    }

    @Test
    void sweepingAgainChangesNothing() {
        long variant = variant(10);
        UUID order = heldOrder(variant, 4);
        letTheHoldRunOut(order);

        sweep.sweepOnce(50);
        ExpirySweep.Result again = sweep.sweepOnce(50);

        assertThat(again).isEqualTo(new ExpirySweep.Result(0, 0));
        assertThat(stockOf(variant)).isEqualTo(10);
    }

    @Test
    void anExpiredReservationBelongingToNoPendingOrderIsStillReleased() {
        long variant = variant(10);
        UUID order = heldOrder(variant, 2);
        jdbc.sql("UPDATE orders SET expires_at = NULL WHERE id = :id").param("id", order).update(); // not overdue
        jdbc.sql("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE order_id = :id")
                .param("id", order).update();

        ExpirySweep.Result result = sweep.sweepOnce(50);

        assertThat(result.ordersCancelled()).isZero();
        assertThat(result.strayReservationsReleased()).isEqualTo(1);
        assertThat(stockOf(variant)).isEqualTo(10);
    }

    @Test
    void fourConcurrentSweepsCancelEachOfTenOrdersAndRestoreItsStockExactlyOnce() throws Exception {
        long variant = variant(10);
        List<UUID> held = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID order = heldOrder(variant, 1);
            letTheHoldRunOut(order);
            held.add(order);
        }
        assertThat(stockOf(variant)).isZero();

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ExpirySweep.Result>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return sweep.sweepOnce(50);
            }));
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).isTrue();

        int cancelled = 0;
        for (Future<ExpirySweep.Result> result : results) {
            cancelled += result.get().ordersCancelled();
        }
        assertThat(cancelled).as("each order cancelled by exactly one sweep").isEqualTo(10);
        assertThat(stockOf(variant)).as("stock restored once, not once per sweep").isEqualTo(10);
        for (UUID order : held) {
            assertThat(statusOf(order)).isEqualTo("cancelled");
            assertThat(jdbc.sql("SELECT count(*) FROM order_status_history WHERE order_id = :id AND to_status = 'cancelled'")
                    .param("id", order).query(Long.class).single()).isEqualTo(1);
        }
    }
}
