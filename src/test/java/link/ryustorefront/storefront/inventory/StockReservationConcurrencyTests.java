package link.ryustorefront.storefront.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The headline correctness test: many buyers, few units, real threads, real Postgres.
 * The pool is sized to the thread count so contention happens in the database, not in the pool.
 */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=60")
@Import(TestcontainersConfiguration.class)
class StockReservationConcurrencyTests {

    private static final Duration TTL = Duration.ofMinutes(15);
    private static final int BUYERS = 50;
    private static final int UNITS = 10;
    private static final int ROUNDS = 20;

    @Autowired
    StockReservations reservations;
    @Autowired
    JdbcClient jdbc;

    InventoryFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new InventoryFixtures(jdbc);
        fx.cleanUp();
    }

    @AfterEach
    void tearDown() {
        fx.cleanUp();
    }

    @Test
    void fiftyBuyersFightingOverTenUnitsYieldExactlyTenReservations_twentyRounds() throws Exception {
        for (int round = 1; round <= ROUNDS; round++) {
            long variant = fx.variant(UNITS);
            List<UUID> orders = orders(BUYERS);

            int succeeded = raceAndCountSuccesses(orders,
                    order -> reservations.reserve(order, Map.of(variant, 1), TTL));

            assertThat(succeeded).as("round %d: successful reservations", round).isEqualTo(UNITS);
            assertThat(fx.stock(variant)).as("round %d: stock left", round).isZero();
            assertThat(fx.reservations(variant, "active")).as("round %d: active reservations", round).isEqualTo(UNITS);
        }
    }

    @Test
    void thirtyBuyersWantingThreeUnitsEachFromTwentyGetExactlySix() throws Exception {
        long variant = fx.variant(20);

        int succeeded = raceAndCountSuccesses(orders(30),
                order -> reservations.reserve(order, Map.of(variant, 3), TTL));

        assertThat(succeeded).isEqualTo(6);
        assertThat(fx.stock(variant)).isEqualTo(2); // 20 - 6 * 3, never negative
        assertThat(fx.reservations(variant, "active")).isEqualTo(6);
    }

    @Test
    void buyersTakingTwoVariantsAtOnceNeverDeadlockAndNeverOversell() throws Exception {
        long a = fx.variant(20);
        long b = fx.variant(20);
        AtomicInteger unexpected = new AtomicInteger();

        // Even-numbered buyers list (a, b), odd-numbered list (b, a): opposite orders are the classic deadlock.
        List<UUID> orders = orders(40);
        int succeeded = raceAndCountSuccesses(orders, order -> {
            boolean even = orders.indexOf(order) % 2 == 0;
            Map<Long, Integer> wanted = even ? Map.of(a, 1, b, 1) : Map.of(b, 1, a, 1);
            try {
                reservations.reserve(order, wanted, TTL);
            } catch (InsufficientStockException e) {
                throw e;
            } catch (RuntimeException e) {
                unexpected.incrementAndGet(); // deadlock, serialisation failure, anything else
                throw e;
            }
            return null;
        });

        assertThat(unexpected).as("errors other than 'not enough stock'").hasValue(0);
        assertThat(succeeded).isEqualTo(20);
        assertThat(fx.stock(a)).isZero();
        assertThat(fx.stock(b)).isZero();
    }

    @Test
    void severalWorkersSweepingExpiredReservationsReleaseEachOneExactlyOnce() throws Exception {
        long variant = fx.variant(100);
        for (int i = 0; i < 100; i++) {
            reservations.reserve(fx.order(), Map.of(variant, 1), TTL);
        }
        fx.expireAllActive();
        assertThat(fx.stock(variant)).isZero();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> workers = new ArrayList<>();
            CountDownLatch go = new CountDownLatch(1);
            for (int w = 0; w < 8; w++) {
                workers.add(pool.submit(() -> {
                    go.await();
                    int total = 0;
                    int released;
                    do {
                        released = reservations.releaseExpired(7);
                        total += released;
                    } while (released > 0);
                    return total;
                }));
            }
            go.countDown();
            int totalReleased = 0;
            for (Future<Integer> worker : workers) {
                totalReleased += worker.get();
            }

            assertThat(totalReleased).isEqualTo(100);
            assertThat(fx.stock(variant)).as("stock restored exactly once per reservation").isEqualTo(100);
            assertThat(fx.reservations(variant, "released")).isEqualTo(100);
            assertThat(fx.reservations(variant, "active")).isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private List<UUID> orders(int count) {
        List<UUID> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(fx.order());
        }
        return orders;
    }

    /** Starts one thread per order, releases them all at the same moment, and counts the ones that did not run out of stock. */
    private int raceAndCountSuccesses(List<UUID> orders, ThrowingConsumer attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(orders.size());
        try {
            CountDownLatch ready = new CountDownLatch(orders.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (UUID order : orders) {
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        attempt.accept(order);
                        return true;
                    } catch (InsufficientStockException e) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            ready.await();
            go.countDown();

            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer {
        Object accept(UUID order) throws Exception;
    }
}
