package link.ryustorefront.storefront.checkout;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.cart.CartTestClient;
import link.ryustorefront.storefront.cart.CartTestClient.Response;
import link.ryustorefront.storefront.checkout.CheckoutFixtures.Item;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Real HTTP against the real server and a real Postgres. Every claim about an order is checked twice:
 * in the JSON the controller produced and in the database rows with independent SQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckoutApiTests {

    private static final String PASSWORD = "correct horse battery";
    private static final String ADDRESS = """
            {"name":"Ada Buyer","line1":"1 Main St","line2":"Apt 4","city":"Portland","region":"OR","postalCode":"97201","country":"US"}""";

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;

    private CheckoutFixtures fixtures;

    @AfterAll
    void cleanUp() {
        new CheckoutFixtures(jdbc).cleanUp();
    }

    private CheckoutFixtures fx() {
        if (fixtures == null) {
            fixtures = new CheckoutFixtures(jdbc);
        }
        return fixtures;
    }

    private String base() {
        return "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class);
    }

    /** A signed-in customer with their own browser. */
    private record Shopper(CartTestClient browser, String email, long id) {
    }

    private Shopper signedIn() throws Exception {
        String email = CheckoutFixtures.uniqueEmail();
        CartTestClient browser = new CartTestClient(base());
        assertThat(browser.register(email, PASSWORD).status()).isEqualTo(201);
        return new Shopper(browser, email, fx().customerIdByEmail(email));
    }

    private static String body(String contactEmail, String address) {
        return "{" + (contactEmail == null ? "" : "\"contactEmail\":\"" + contactEmail + "\",")
                + "\"shippingAddress\":" + address + "}";
    }

    private static Map<String, String> key(String value) {
        return Map.of("Idempotency-Key", value);
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private Response checkout(Shopper s, String key) throws Exception {
        return s.browser().post("/api/checkout", body(null, ADDRESS), key(key));
    }

    private void addToCart(Shopper s, Item item, int quantity) throws Exception {
        assertThat(s.browser().put("/api/cart/items/" + item.sku(), CartTestClient.qty(quantity)).status()).isEqualTo(200);
    }

    private long count(String sql, Object... params) {
        var spec = jdbc.sql(sql);
        for (int i = 0; i < params.length; i++) {
            spec = spec.param(params[i]);
        }
        return spec.query(Long.class).single();
    }

    private long ordersOf(Shopper s) {
        return count("SELECT count(*) FROM orders WHERE customer_id = ?", s.id());
    }

    // ---- who may check out --------------------------------------------------------------------

    @Test
    void aGuestCanFillACartButCannotCheckOut() throws Exception {
        Item item = fx().variant(5, 12990);
        CartTestClient guest = CartTestClient.guest(base());
        guest.put("/api/cart/items/" + item.sku(), CartTestClient.qty(1));

        Response r = guest.post("/api/checkout", body("guest@example.com", ADDRESS), key(newKey()));

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.str("$.code")).isEqualTo("unauthenticated");
        assertThat(fx().stock(item)).as("nothing reserved").isEqualTo(5);
        assertThat(count("SELECT count(*) FROM orders WHERE contact_email = ?", "guest@example.com")).isZero();
    }

    @Test
    void aSignedInPostWithoutACsrfTokenIsRejected() throws Exception {
        Shopper s = signedIn();
        Item item = fx().variant(5, 1000);
        addToCart(s, item, 1);

        Response r = s.browser().postWithoutCsrf("/api/checkout", body(null, ADDRESS), key(newKey()));

        assertThat(r.status()).isEqualTo(403);
        assertThat(r.str("$.code")).isEqualTo("csrf_invalid");
        assertThat(ordersOf(s)).isZero();
        assertThat(fx().stock(item)).isEqualTo(5);
    }

    // ---- the happy path: one order, snapshot, reservation -------------------------------------

    @Test
    void checkoutCreatesAPendingOrderASnapshotAndReservationsInTheDatabase() throws Exception {
        Shopper s = signedIn();
        Item a = fx().variant(10, 12990);
        Item b = fx().variant(10, 5000);
        addToCart(s, a, 2);
        addToCart(s, b, 1);
        Instant before = Instant.now();

        Response r = checkout(s, newKey());

        assertThat(r.status()).isEqualTo(201);
        String orderId = r.str("$.id");
        assertThat(r.str("$.status")).isEqualTo("pending");
        assertThat(r.str("$.contactEmail")).as("defaults to the account email").isEqualTo(s.email());
        assertThat(r.str("$.currency")).isEqualTo("USD");
        assertThat(r.num("$.subtotalMinor")).isEqualTo(30_980);
        assertThat(r.num("$.shippingMinor")).isEqualTo(800);
        assertThat(r.num("$.totalMinor")).isEqualTo(31_780);
        assertThat(r.str("$.items[0].sku")).isEqualTo(a.sku());
        assertThat(r.num("$.items[0].lineTotalMinor")).isEqualTo(25_980);
        assertThat(r.str("$.items[1].sku")).isEqualTo(b.sku());
        Instant expires = Instant.parse(r.str("$.expiresAt"));
        assertThat(Duration.between(before, expires).toMinutes()).as("about 30 minutes ahead").isBetween(29L, 30L);

        // Independent SQL: the order row.
        Map<String, Object> order = jdbc.sql("SELECT * FROM orders WHERE id = :id").param("id", UUID.fromString(orderId))
                .query().singleRow();
        assertThat(order.get("customer_id")).isEqualTo(s.id());
        assertThat(order.get("status")).isEqualTo("pending");
        assertThat(order.get("subtotal_minor")).isEqualTo(30_980L);
        assertThat(order.get("shipping_minor")).isEqualTo(800L);
        assertThat(order.get("total_minor")).isEqualTo(31_780L);
        assertThat(order.get("contact_email")).isEqualTo(s.email());
        // The address went into jsonb from bound parameters.
        assertThat(jdbc.sql("SELECT shipping_address->>'postalCode' FROM orders WHERE id = :id")
                .param("id", UUID.fromString(orderId)).query(String.class).single()).isEqualTo("97201");
        assertThat(jdbc.sql("SELECT shipping_address->>'line2' FROM orders WHERE id = :id")
                .param("id", UUID.fromString(orderId)).query(String.class).single()).isEqualTo("Apt 4");
        // Item snapshot, reservations, stock and history.
        assertThat(count("SELECT count(*) FROM order_items WHERE order_id = ?::uuid", orderId)).isEqualTo(2);
        assertThat(count("SELECT unit_price_minor FROM order_items WHERE order_id = ?::uuid AND sku = ?", orderId, a.sku()))
                .isEqualTo(12_990);
        assertThat(count("SELECT count(*) FROM reservations WHERE order_id = ?::uuid AND status = 'active'", orderId)).isEqualTo(2);
        assertThat(count("SELECT quantity FROM reservations WHERE order_id = ?::uuid AND variant_id = ?", orderId, a.id()))
                .isEqualTo(2);
        assertThat(fx().stock(a)).isEqualTo(8);
        assertThat(fx().stock(b)).isEqualTo(9);
        assertThat(jdbc.sql("""
                SELECT o.expires_at = (SELECT min(expires_at) FROM reservations WHERE order_id = o.id)
                FROM orders o WHERE o.id = :id""").param("id", UUID.fromString(orderId)).query(Boolean.class).single())
                .as("the order expires when its reservations do").isTrue();
        assertThat(jdbc.sql("SELECT from_status IS NULL AND to_status = 'pending' FROM order_status_history WHERE order_id = :id")
                .param("id", UUID.fromString(orderId)).query(Boolean.class).single()).isTrue();

        // The cart is kept until payment (phase 4 clears it).
        assertThat(s.browser().get("/api/cart").quantities()).containsOnlyKeys(a.sku(), b.sku());
    }

    @Test
    void theOrderKeepsTheCheckoutPriceWhenTheCatalogueChangesLater() throws Exception {
        Shopper s = signedIn();
        Item item = fx().variant(10, 12990);
        addToCart(s, item, 2);
        String orderId = checkout(s, newKey()).str("$.id");

        fx().setPrice(item, 99_900);

        assertThat(count("SELECT unit_price_minor FROM order_items WHERE order_id = ?::uuid", orderId)).isEqualTo(12_990);
        assertThat(count("SELECT total_minor FROM orders WHERE id = ?::uuid", orderId)).isEqualTo(26_780);
        assertThat(s.browser().get("/api/cart").num("$.lines[0].unitPriceMinor")).as("the cart is live").isEqualTo(99_900);
    }

    @Test
    void aContactEmailInTheRequestOverridesTheAccountEmail() throws Exception {
        Shopper s = signedIn();
        addToCart(s, fx().variant(5, 1000), 1);

        Response r = s.browser().post("/api/checkout", body("gift.recipient@example.com", ADDRESS), key(newKey()));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.str("$.contactEmail")).isEqualTo("gift.recipient@example.com");
    }

    // ---- idempotency --------------------------------------------------------------------------

    @Test
    void repeatingARequestWithTheSameKeyReturnsTheSameOrderAndReservesNothingNew() throws Exception {
        Shopper s = signedIn();
        Item item = fx().variant(10, 1000);
        addToCart(s, item, 2);
        String key = newKey();

        Response first = checkout(s, key);
        Response again = checkout(s, key);
        Response third = checkout(s, key);

        assertThat(first.status()).isEqualTo(201);
        assertThat(again.status()).isEqualTo(200);
        assertThat(third.status()).isEqualTo(200);
        assertThat(again.str("$.id")).isEqualTo(first.str("$.id"));
        assertThat(third.str("$.id")).isEqualTo(first.str("$.id"));
        assertThat(ordersOf(s)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reservations WHERE variant_id = ?", item.id())).isEqualTo(1);
        assertThat(fx().stock(item)).as("reserved once, not three times").isEqualTo(8);
    }

    @Test
    void theSameKeyForTwoDifferentCustomersIsTwoDifferentOrders() throws Exception {
        Item item = fx().variant(10, 1000);
        Shopper one = signedIn();
        Shopper two = signedIn();
        addToCart(one, item, 1);
        addToCart(two, item, 1);
        String shared = newKey();

        Response a = checkout(one, shared);
        Response b = checkout(two, shared);

        assertThat(a.status()).isEqualTo(201);
        assertThat(b.status()).isEqualTo(201);
        assertThat(a.str("$.id")).isNotEqualTo(b.str("$.id"));
    }

    @Test
    void theDatabaseItselfRejectsADuplicateKeyForOneCustomer() {
        long customer = fx().customer();
        String insert = "INSERT INTO orders (customer_id, contact_email, subtotal_minor, shipping_minor, total_minor, "
                + "shipping_address, idempotency_key) VALUES (:c, 'x@example.com', 0, 0, 0, '{}'::jsonb, :k)";
        jdbc.sql(insert).param("c", customer).param("k", "duplicate-key-1").update();

        try {
            jdbc.sql(insert).param("c", customer).param("k", "duplicate-key-1").update();
            throw new AssertionError("expected the unique index to reject the second order");
        } catch (DuplicateKeyException e) {
            assertThat(e.getMessage()).contains("orders_customer_idempotency_uniq");
        }
    }

    // ---- realistic behaviour: a new checkout replaces the abandoned one -----------------------

    @Test
    void startingAgainWithANewKeyCancelsThePreviousPendingOrderAndFreesItsStockFirst() throws Exception {
        Shopper s = signedIn();
        Item last = fx().variant(1, 20_000); // the last pair
        addToCart(s, last, 1);
        String firstKey = newKey();
        Response first = checkout(s, firstKey);
        assertThat(first.status()).isEqualTo(201);
        assertThat(fx().stock(last)).isZero();

        Response second = checkout(s, newKey());

        assertThat(second.status()).as("the earlier attempt must not block the retry for the last pair").isEqualTo(201);
        assertThat(second.str("$.id")).isNotEqualTo(first.str("$.id"));
        UUID firstId = UUID.fromString(first.str("$.id"));
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", firstId).query(String.class).single())
                .isEqualTo("cancelled");
        assertThat(jdbc.sql("SELECT reason FROM order_status_history WHERE order_id = :id AND to_status = 'cancelled'")
                .param("id", firstId).query(String.class).single()).isEqualTo("superseded by a new checkout");
        assertThat(count("SELECT count(*) FROM reservations WHERE order_id = ?::uuid AND status = 'released'", first.str("$.id")))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM reservations WHERE variant_id = ? AND status = 'active'", last.id())).isEqualTo(1);
        assertThat(fx().stock(last)).as("exactly one unit held, by the new order").isZero();
        assertThat(count("SELECT count(*) FROM orders WHERE customer_id = ? AND status = 'pending'", s.id())).isEqualTo(1);

        // Replaying the first key shows its true state instead of resurrecting it.
        Response replay = checkout(s, firstKey);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.str("$.id")).isEqualTo(first.str("$.id"));
        assertThat(replay.str("$.status")).isEqualTo("cancelled");
    }

    // ---- failures change nothing --------------------------------------------------------------

    @Test
    void aFailedAttemptRollsBackEverythingAndLeavesTheEarlierPendingOrderAlone() throws Exception {
        Shopper s = signedIn();
        Item a = fx().variant(10, 1000);
        addToCart(s, a, 1);
        Response earlier = checkout(s, newKey());
        assertThat(earlier.status()).isEqualTo(201);
        assertThat(fx().stock(a)).isEqualTo(9);

        // A second line sells out between the shopper's PUT and checkout (another buyer took it).
        Item b = fx().variant(1, 2000);
        addToCart(s, b, 1);
        fx().setStock(b, 0);

        Response r = checkout(s, newKey());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("cart_changed");
        assertThat(r.str("$.fields['" + b.sku() + "']")).isEqualTo("stock");
        assertThat(ordersOf(s)).as("no new order").isEqualTo(1);
        assertThat(jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", UUID.fromString(earlier.str("$.id")))
                .query(String.class).single()).as("the earlier order was not cancelled").isEqualTo("pending");
        assertThat(count("SELECT count(*) FROM reservations WHERE order_id = ?::uuid AND status = 'active'", earlier.str("$.id")))
                .isEqualTo(1);
        assertThat(fx().stock(a)).as("stock held by the earlier order is unchanged").isEqualTo(9);
        assertThat(fx().stock(b)).isZero();
    }

    @Test
    void everyProblemLineIsReportedAtOnce() throws Exception {
        Shopper s = signedIn();
        Item gone = fx().variant(5, 1000);
        Item low = fx().variant(5, 1000);
        Item fine = fx().variant(5, 1000);
        addToCart(s, gone, 1);
        addToCart(s, low, 3);
        addToCart(s, fine, 1);
        fx().setVariantActive(gone, false);
        fx().setStock(low, 2);

        Response r = checkout(s, newKey());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("cart_changed");
        assertThat(r.str("$.fields['" + gone.sku() + "']")).isEqualTo("unavailable");
        assertThat(r.str("$.fields['" + low.sku() + "']")).isEqualTo("stock");
        assertThat(ordersOf(s)).isZero();
        assertThat(fx().stock(fine)).isEqualTo(5);
        assertThat(fx().stock(low)).isEqualTo(2);
    }

    @Test
    void anEmptyCartIsA409AndCreatesNothing() throws Exception {
        Shopper s = signedIn();

        Response r = checkout(s, newKey());

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("empty_cart");
        assertThat(ordersOf(s)).isZero();
    }

    // ---- validation ---------------------------------------------------------------------------

    @Test
    void aMissingOrMalformedIdempotencyKeyIsRejectedBeforeAnythingHappens() throws Exception {
        Shopper s = signedIn();
        Item item = fx().variant(5, 1000);
        addToCart(s, item, 1);

        Response missing = s.browser().post("/api/checkout", body(null, ADDRESS), Map.of());
        Response tooShort = checkout(s, "short");
        Response badChars = checkout(s, "has spaces in it!");

        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.str("$.fields.idempotencyKey")).isEqualTo("required");
        assertThat(tooShort.str("$.fields.idempotencyKey")).isEqualTo("invalid_format");
        assertThat(badChars.str("$.fields.idempotencyKey")).isEqualTo("invalid_format");
        assertThat(ordersOf(s)).isZero();
        assertThat(fx().stock(item)).isEqualTo(5);
    }

    @Test
    void invalidAddressesAndEmailsReturnFieldCodesAndCreateNothing() throws Exception {
        Shopper s = signedIn();
        Item item = fx().variant(5, 1000);
        addToCart(s, item, 1);
        String noPostal = """
                {"name":"Ada","line1":"1 Main St","city":"Portland","region":"OR","country":"US"}""";
        String badCountry = """
                {"name":"Ada","line1":"1 Main St","city":"Portland","region":"OR","postalCode":"97201","country":"usa"}""";

        Response missingPostal = s.browser().post("/api/checkout", body(null, noPostal), key(newKey()));
        Response country = s.browser().post("/api/checkout", body(null, badCountry), key(newKey()));
        Response noAddress = s.browser().post("/api/checkout", "{}", key(newKey()));
        Response badEmail = s.browser().post("/api/checkout", body("not-an-email", ADDRESS), key(newKey()));
        s.browser().post("/api/checkout", "{oops", key(newKey()));

        assertThat(missingPostal.status()).isEqualTo(400);
        assertThat(missingPostal.str("$.fields['shippingAddress.postalCode']")).isEqualTo("required");
        assertThat(country.str("$.fields['shippingAddress.country']")).isEqualTo("invalid_country");
        assertThat(noAddress.str("$.fields.shippingAddress")).isEqualTo("required");
        assertThat(badEmail.str("$.fields.contactEmail")).isEqualTo("invalid_email");
        assertThat(ordersOf(s)).isZero();
        assertThat(fx().stock(item)).isEqualTo(5);
    }

    // ---- the cart handed over by sign-in ------------------------------------------------------

    @Test
    void aGuestCartMergedAtSignInCanBeCheckedOut() throws Exception {
        Item item = fx().variant(5, 7_000);
        CartTestClient browser = CartTestClient.guest(base());
        browser.put("/api/cart/items/" + item.sku(), CartTestClient.qty(2));
        String email = CheckoutFixtures.uniqueEmail();

        assertThat(browser.register(email, PASSWORD).status()).isEqualTo(201);
        Response r = browser.post("/api/checkout", body(null, ADDRESS), key(newKey()));

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.str("$.items[0].sku")).isEqualTo(item.sku());
        assertThat(r.num("$.subtotalMinor")).isEqualTo(14_000);
        assertThat(fx().stock(item)).isEqualTo(3);
    }
}
