package link.ryustorefront.storefront.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.cart.CartTestClient;
import link.ryustorefront.storefront.cart.CartTestClient.Response;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.Orders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Real HTTP against the real server and Postgres, with the fake gateway standing in for Stripe. Every
 * claim is checked in the JSON and again in the database rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentApiTests {

    private static final String PASSWORD = "correct horse battery";
    private static final String ADDRESS = """
            {"name":"Ada Buyer","line1":"1 Main St","city":"Portland","region":"OR","postalCode":"97201","country":"US"}""";

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    Orders orders;

    @BeforeEach
    void resetFake() {
        FakePaymentGateway.reset();
    }

    @AfterAll
    void cleanUp() {
        String mine = "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id WHERE c.email LIKE 'pay-test-%'";
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM reservations WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_items WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE email LIKE 'pay-test-%')").update();
        jdbc.sql("DELETE FROM cart_items WHERE variant_id IN (SELECT id FROM variants WHERE sku LIKE 'PAYTEST-%')").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'PAYTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'pay-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'pay-test-c-%'").update();
        jdbc.sql("DELETE FROM customers WHERE email LIKE 'pay-test-%'").update();
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private record Shopper(CartTestClient browser, String email, long id) {
    }

    private record Placed(Shopper shopper, UUID orderId, String sku) {
    }

    private String base() {
        return "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class);
    }

    private Shopper signedIn() throws Exception {
        String email = "pay-test-" + UUID.randomUUID() + "@example.com";
        CartTestClient browser = new CartTestClient(base());
        assertThat(browser.register(email, PASSWORD).status()).isEqualTo(201);
        long id = jdbc.sql("SELECT id FROM customers WHERE email = :e").param("e", email).query(Long.class).single();
        return new Shopper(browser, email, id);
    }

    private String variantSku() {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'Test') RETURNING id")
                .param("s", "pay-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("INSERT INTO products (slug, name, brand, category_id, gender) "
                        + "VALUES (:s, 'Pay test boot', 'Test', :c, 'unisex') RETURNING id")
                .param("s", "pay-test-p-" + id).param("c", category).query(Long.class).single();
        String sku = "PAYTEST-" + id;
        jdbc.sql("INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock) "
                        + "VALUES (:p, :sku, '42', 'black', 12990, 20)").param("p", product).param("sku", sku).update();
        return sku;
    }

    /** A signed-in shopper with a real pending order (cart, then POST /api/checkout) of 2 x 129.90 USD. */
    private Placed pendingOrder() throws Exception {
        Shopper shopper = signedIn();
        String sku = variantSku();
        assertThat(shopper.browser().put("/api/cart/items/" + sku, CartTestClient.qty(2)).status()).isEqualTo(200);
        Response checkout = shopper.browser().post("/api/checkout",
                "{\"shippingAddress\":" + ADDRESS + "}", Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(checkout.status()).isEqualTo(201);
        return new Placed(shopper, UUID.fromString(checkout.str("$.id")), sku);
    }

    private Response pay(Shopper s, UUID orderId) throws Exception {
        return s.browser().post("/api/orders/" + orderId + "/pay", null, Map.of());
    }

    private long minutesAhead(String sql, UUID orderId) {
        return jdbc.sql("SELECT round(EXTRACT(EPOCH FROM (" + sql + " - now())) / 60) FROM orders o WHERE o.id = :id")
                .param("id", orderId).query(Long.class).single();
    }

    // ---- the happy path ---------------------------------------------------------------------

    @Test
    void payingCreatesOnePaymentPageStoresItAndExtendsTheHoldPastIt() throws Exception {
        Placed order = pendingOrder();
        Instant before = Instant.now();

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(200);
        assertThat(FakePaymentGateway.REQUESTS).hasSize(1);
        CheckoutRequest sent = FakePaymentGateway.REQUESTS.get(0);
        assertThat(sent.orderId()).isEqualTo(order.orderId());
        assertThat(sent.customerEmail()).isEqualTo(order.shopper().email());
        assertThat(sent.currency()).isEqualTo("USD");
        assertThat(sent.lines()).containsExactly(
                new CheckoutRequest.Line("Pay test boot (size 42, black)", 12_990, 2),
                new CheckoutRequest.Line("Shipping", 800, 1));
        assertThat(sent.successUrl()).isEqualTo("http://localhost:3000/orders/" + order.orderId()
                + "/confirmation?session_id={CHECKOUT_SESSION_ID}");
        assertThat(sent.cancelUrl()).isEqualTo("http://localhost:3000/cart");
        assertThat(Duration.between(before, sent.expiresAt()).toMinutes()).as("Stripe needs at least 30 minutes")
                .isBetween(31L, 32L);

        assertThat(r.str("$.url")).startsWith("https://checkout.stripe.test/c/pay/cs_test_fake_");
        assertThat(Instant.parse(r.str("$.expiresAt"))).isEqualTo(sent.expiresAt());

        // Stored on the order, all three columns together.
        Map<String, Object> row = jdbc.sql("SELECT stripe_checkout_session_id, payment_url, payment_expires_at "
                + "FROM orders WHERE id = :id").param("id", order.orderId()).query().singleRow();
        assertThat((String) row.get("stripe_checkout_session_id")).startsWith("cs_test_fake_");
        assertThat((String) row.get("payment_url")).isEqualTo(r.str("$.url"));
        assertThat(row.get("payment_expires_at")).isNotNull();

        // The hold now outlives the page by the grace period, on the order and on its reservations alike.
        assertThat(minutesAhead("o.payment_expires_at", order.orderId())).as("page lasts about 31 minutes").isEqualTo(31L);
        assertThat(minutesAhead("o.expires_at", order.orderId())).as("order hold = page + 5 minutes").isEqualTo(36L);
        assertThat(jdbc.sql("""
                SELECT bool_and(r.expires_at = o.expires_at) FROM reservations r JOIN orders o ON o.id = r.order_id
                WHERE o.id = :id AND r.status = 'active'""").param("id", order.orderId()).query(Boolean.class).single())
                .as("reservations expire exactly when the order's hold does").isTrue();
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", order.orderId()).query(String.class)
                .single()).as("paying has not been done yet, only started").isEqualTo("pending");
    }

    @Test
    void repeatingPayReturnsTheSamePageWithoutAnotherProviderCall() throws Exception {
        Placed order = pendingOrder();

        Response first = pay(order.shopper(), order.orderId());
        Response second = pay(order.shopper(), order.orderId());
        Response third = pay(order.shopper(), order.orderId());

        assertThat(second.status()).isEqualTo(200);
        assertThat(second.str("$.url")).isEqualTo(first.str("$.url"));
        assertThat(third.str("$.url")).isEqualTo(first.str("$.url"));
        assertThat(second.str("$.expiresAt")).isEqualTo(first.str("$.expiresAt"));
        assertThat(FakePaymentGateway.REQUESTS).as("one session at the provider").hasSize(1);
        assertThat(minutesAhead("o.expires_at", order.orderId())).as("the hold was extended once, not three times")
                .isEqualTo(36L);
    }

    @Test
    void fiveSimultaneousPayCallsCreateExactlyOneSession() throws Exception {
        Placed order = pendingOrder();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        List<Future<Response>> calls = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            calls.add(pool.submit(() -> pay(order.shopper(), order.orderId())));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        String url = null;
        for (Future<Response> call : calls) {
            Response r = call.get();
            assertThat(r.status()).isEqualTo(200);
            url = url == null ? r.str("$.url") : url;
            assertThat(r.str("$.url")).isEqualTo(url);
        }
        assertThat(FakePaymentGateway.REQUESTS).hasSize(1);
        assertThat(jdbc.sql("SELECT count(*) FROM orders WHERE stripe_checkout_session_id IS NOT NULL AND id = :id")
                .param("id", order.orderId()).query(Long.class).single()).isEqualTo(1);
    }

    // ---- who may pay what -------------------------------------------------------------------

    @Test
    void aGuestIsRejectedAndSomeoneElsesOrderLooksLikeItDoesNotExist() throws Exception {
        Placed order = pendingOrder();
        CartTestClient guest = CartTestClient.guest(base());
        Shopper stranger = signedIn();

        Response asGuest = guest.post("/api/orders/" + order.orderId() + "/pay", null, Map.of());
        Response asStranger = pay(stranger, order.orderId());
        Response unknown = pay(order.shopper(), UUID.randomUUID());
        Response notAnId = order.shopper().browser().post("/api/orders/not-a-uuid/pay", null, Map.of());

        assertThat(asGuest.status()).isEqualTo(401);
        assertThat(asStranger.status()).isEqualTo(404);
        assertThat(asStranger.str("$.code")).isEqualTo("order_not_found");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.body()).as("no difference between 'not yours' and 'not there'").isEqualTo(asStranger.body());
        assertThat(notAnId.status()).isEqualTo(400);
        assertThat(FakePaymentGateway.REQUESTS).isEmpty();
    }

    @Test
    void aPostWithoutACsrfTokenIsRejected() throws Exception {
        Placed order = pendingOrder();

        Response r = order.shopper().browser().postWithoutCsrf("/api/orders/" + order.orderId() + "/pay", null, Map.of());

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.str("$.code")).isEqualTo("csrf_invalid");
        assertThat(FakePaymentGateway.REQUESTS).isEmpty();
    }

    // ---- orders that cannot be paid ---------------------------------------------------------

    @Test
    void anOrderThatIsNotPendingCannotBePaid() throws Exception {
        Placed order = pendingOrder();
        assertThat(orders.transition(order.orderId(), OrderStatus.CANCELLED, "test").applied()).isTrue();

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("order_not_payable");
        assertThat(FakePaymentGateway.REQUESTS).isEmpty();
    }

    @Test
    void anOrderWhoseHoldHasRunOutCannotBePaid() throws Exception {
        Placed order = pendingOrder();
        jdbc.sql("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = :id").param("id", order.orderId()).update();

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("order_expired");
        assertThat(FakePaymentGateway.REQUESTS).isEmpty();
    }

    @Test
    void anExpiredPaymentPageIsNotRecreatedSoTheHoldCannotBeStretchedForEver() throws Exception {
        Placed order = pendingOrder();
        pay(order.shopper(), order.orderId());
        jdbc.sql("UPDATE orders SET payment_expires_at = now() - interval '1 minute' WHERE id = :id")
                .param("id", order.orderId()).update();

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("payment_expired");
        assertThat(FakePaymentGateway.REQUESTS).as("no second session").hasSize(1);
        assertThat(minutesAhead("o.expires_at", order.orderId())).as("the hold was not extended again").isEqualTo(36L);
    }

    // ---- the provider fails -----------------------------------------------------------------

    @Test
    void aProviderFailureLeavesTheOrderAndItsHoldExactlyAsTheyWere() throws Exception {
        Placed order = pendingOrder();
        Instant holdBefore = jdbc.sql("SELECT expires_at FROM orders WHERE id = :id").param("id", order.orderId())
                .query((rs, n) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant()).single();
        FakePaymentGateway.failWith = new PaymentProviderException("Stripe is down", new RuntimeException("boom"));

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(502);
        assertThat(r.str("$.code")).isEqualTo("payment_provider_error");
        assertThat(jdbc.sql("SELECT stripe_checkout_session_id IS NULL AND payment_url IS NULL FROM orders WHERE id = :id")
                .param("id", order.orderId()).query(Boolean.class).single()).isTrue();
        Instant holdAfter = jdbc.sql("SELECT expires_at FROM orders WHERE id = :id").param("id", order.orderId())
                .query((rs, n) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant()).single();
        assertThat(holdAfter).isEqualTo(holdBefore);

        // ...and it can be retried once the provider is back.
        FakePaymentGateway.failWith = null;
        assertThat(pay(order.shopper(), order.orderId()).status()).isEqualTo(200);
    }

    @Test
    void withNoProviderConfiguredPayAnswers503() throws Exception {
        Placed order = pendingOrder();
        FakePaymentGateway.failWith = new PaymentsNotConfiguredException();

        Response r = pay(order.shopper(), order.orderId());

        assertThat(r.status()).isEqualTo(503);
        assertThat(r.str("$.code")).isEqualTo("payments_unavailable");
    }

    // ---- the schema -------------------------------------------------------------------------

    @Test
    void theDatabaseRefusesAPaymentUrlWithoutASessionAndAnExpiry() throws Exception {
        Placed order = pendingOrder();

        try {
            jdbc.sql("UPDATE orders SET payment_url = 'https://x.test' WHERE id = :id").param("id", order.orderId()).update();
            throw new AssertionError("expected the CHECK constraint to reject a half-set session");
        } catch (DataIntegrityViolationException e) {
            assertThat(e.getMessage()).contains("orders_payment_session_complete");
        }
    }
}
