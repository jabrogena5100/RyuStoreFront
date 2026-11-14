package link.ryustorefront.storefront.checkout;

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
import link.ryustorefront.storefront.checkout.CheckoutFixtures.Item;
import link.ryustorefront.storefront.orders.ShippingAddress;
import link.ryustorefront.storefront.shared.ApiException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Many buyers, one scarce variant, through the real checkout service and a real Postgres: exactly as
 * many orders as there are units, and the stock, reservation and order counts agree. Called directly
 * (not over HTTP) so the contention is on the database, not on 20 logins.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckoutConcurrencyTests {

    private static final int BUYERS = 20;
    private static final int UNITS = 5;
    private static final int ROUNDS = 3;

    @Autowired
    CheckoutService checkout;
    @Autowired
    JdbcClient jdbc;

    @AfterAll
    void cleanUp() {
        new CheckoutFixtures(jdbc).cleanUp();
    }

    @Test
    void twentyBuyersForFiveUnitsYieldExactlyFiveOrdersInEveryRound() throws Exception {
        CheckoutFixtures fx = new CheckoutFixtures(jdbc);
        List<Long> customers = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            customers.add(fx.customer());
        }
        StartCheckoutRequest request = new StartCheckoutRequest(null,
                new ShippingAddress("A Buyer", "1 Main St", null, "Portland", "OR", "97201", "US"));

        for (int round = 1; round <= ROUNDS; round++) {
            Item item = fx.variant(UNITS, 10_000);
            for (long customer : customers) {
                fx.cartOf(customer, item, 1);
            }

            ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<String>> outcomes = new ArrayList<>();
            for (long customer : customers) {
                outcomes.add(pool.submit(() -> {
                    go.await();
                    try {
                        checkout.start(customer, fx.emailOf(customer), UUID.randomUUID().toString(), request);
                        return "ok";
                    } catch (ApiException e) {
                        return e.body().code();
                    }
                }));
            }
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(120, TimeUnit.SECONDS)).as("round %d finished", round).isTrue();

            int ok = 0;
            int soldOut = 0;
            for (Future<String> outcome : outcomes) {
                String result = outcome.get();
                if (result.equals("ok")) {
                    ok++;
                } else if (result.equals("cart_changed")) {
                    soldOut++;
                } else {
                    throw new AssertionError("unexpected outcome in round " + round + ": " + result);
                }
            }
            assertThat(ok).as("round %d: successful checkouts", round).isEqualTo(UNITS);
            assertThat(soldOut).as("round %d: turned away", round).isEqualTo(BUYERS - UNITS);

            // Independent SQL oracles, not the service's own view.
            assertThat(fx.stock(item)).as("round %d: stock left", round).isZero();
            long reserved = jdbc.sql("SELECT coalesce(sum(quantity), 0) FROM reservations WHERE variant_id = :v AND status = 'active'")
                    .param("v", item.id()).query(Long.class).single();
            long orderRows = jdbc.sql("SELECT count(*) FROM order_items WHERE variant_id = :v").param("v", item.id())
                    .query(Long.class).single();
            long pendingOrders = jdbc.sql("""
                    SELECT count(DISTINCT o.id) FROM orders o JOIN order_items i ON i.order_id = o.id
                    WHERE i.variant_id = :v AND o.status = 'pending'""").param("v", item.id()).query(Long.class).single();
            assertThat(reserved).as("round %d: units held", round).isEqualTo(UNITS);
            assertThat(orderRows).as("round %d: order lines", round).isEqualTo(UNITS);
            assertThat(pendingOrders).as("round %d: pending orders", round).isEqualTo(UNITS);
        }
    }
}
