package link.ryustorefront.storefront.cart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.cart.CartFixtures.Item;
import link.ryustorefront.storefront.cart.CartTestClient.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Real HTTP against the real server and a real Postgres. The CART cookie is read from the actual
 * Set-Cookie header, and cart state is checked with independent SQL (including a SHA-256 computed by
 * Postgres, not by the code under test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CartApiTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;

    private CartFixtures fixtures;

    @AfterAll
    void cleanUp() {
        new CartFixtures(jdbc).cleanUp();
    }

    private CartFixtures fixtures() {
        if (fixtures == null) {
            fixtures = new CartFixtures(jdbc);
        }
        return fixtures;
    }

    private String base() {
        return "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class);
    }

    private CartTestClient guest() throws Exception {
        return CartTestClient.guest(base());
    }

    private static String uniqueEmail() {
        return "shopper-" + UUID.randomUUID() + "@example.com";
    }

    private static String path(Item item) {
        return "/api/cart/items/" + item.sku();
    }

    private long cartsWithTokenHash(String token) {
        return jdbc.sql("SELECT count(*) FROM carts WHERE guest_token_hash = encode(sha256(convert_to(:t, 'UTF8')), 'hex')")
                .param("t", token).query(Long.class).single();
    }

    private long cartsWithPlainValue(String value) {
        return jdbc.sql("SELECT count(*) FROM carts WHERE guest_token_hash = :t").param("t", value).query(Long.class).single();
    }

    private long allCarts() {
        return jdbc.sql("SELECT count(*) FROM carts").query(Long.class).single();
    }

    private long itemRowsForGuest(String token) {
        return jdbc.sql("SELECT count(*) FROM cart_items WHERE cart_id = (SELECT id FROM carts "
                        + "WHERE guest_token_hash = encode(sha256(convert_to(:t, 'UTF8')), 'hex'))")
                .param("t", token).query(Long.class).single();
    }

    private long customerCartItems(String email) {
        return jdbc.sql("SELECT count(*) FROM cart_items WHERE cart_id = (SELECT c.id FROM carts c "
                        + "JOIN customers u ON u.id = c.customer_id WHERE u.email = :e)")
                .param("e", email).query(Long.class).single();
    }

    // ---- reading ----------------------------------------------------------------------------

    @Test
    void anAnonymousReadReturnsAnEmptyCartAndCreatesNothing() throws Exception {
        CartTestClient b = new CartTestClient(base());
        long before = allCarts();

        Response r = b.get("/api/cart");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.num("$.itemCount")).isZero();
        assertThat(r.num("$.subtotalMinor")).isZero();
        assertThat(r.str("$.currency")).isEqualTo("USD");
        assertThat(r.quantities()).isEmpty();
        assertThat(r.headers().allValues("set-cookie")).as("reading must not create a session or a cart cookie").isEmpty();
        assertThat(allCarts()).isEqualTo(before);
    }

    // ---- guest cart: cookie and storage -----------------------------------------------------

    @Test
    void theFirstGuestWriteCreatesACartAndAnHttpOnlyLaxCookieWhoseValueIsNotStored() throws Exception {
        Item item = fixtures().variant(10, 12990);
        CartTestClient b = guest();

        Response r = b.put(path(item), CartTestClient.qty(2));

        assertThat(r.status()).isEqualTo(200);
        String setCookie = r.setCookie("CART");
        assertThat(setCookie).as("Set-Cookie headers: %s", r.headers().allValues("set-cookie")).isNotNull();
        assertThat(CartTestClient.attributes(setCookie))
                .contains("httponly", "samesite=lax", "path=/", "max-age=2592000")
                .doesNotContain("secure");
        String token = b.cookies.get("CART");
        assertThat(token).hasSize(43);
        assertThat(cartsWithTokenHash(token)).as("the SHA-256 of the token identifies the cart").isEqualTo(1);
        assertThat(cartsWithPlainValue(token)).as("the raw token must never be stored").isZero();

        assertThat(r.num("$.itemCount")).isEqualTo(2);
        assertThat(r.str("$.lines[0].sku")).isEqualTo(item.sku());
        assertThat(r.num("$.lines[0].unitPriceMinor")).isEqualTo(12990);
        assertThat(r.num("$.lines[0].lineTotalMinor")).isEqualTo(25980);
        assertThat(r.num("$.subtotalMinor")).isEqualTo(25980);
        assertThat(r.bool("$.lines[0].available")).isTrue();
    }

    @Test
    void settingTheSameQuantityTwiceChangesNothingAndRenewsTheCookie() throws Exception {
        Item item = fixtures().variant(10, 5000);
        CartTestClient b = guest();
        b.put(path(item), CartTestClient.qty(2));
        String token = b.cookies.get("CART");

        Response again = b.put(path(item), CartTestClient.qty(2));

        assertThat(again.quantities()).containsEntry(item.sku(), 2).hasSize(1);
        assertThat(itemRowsForGuest(token)).isEqualTo(1);
        assertThat(again.setCookie("CART")).as("a write slides the expiry forward").startsWith("CART=" + token);
        assertThat(b.cookies.get("CART")).isEqualTo(token);
    }

    @Test
    void quantityZeroAndDeleteRemoveALineAndBothAreIdempotent() throws Exception {
        Item a = fixtures().variant(10, 1000);
        Item c = fixtures().variant(10, 2000);
        CartTestClient b = guest();
        b.put(path(a), CartTestClient.qty(1));
        b.put(path(c), CartTestClient.qty(3));

        Response viaZero = b.put(path(a), CartTestClient.qty(0));
        assertThat(viaZero.quantities()).containsOnlyKeys(c.sku());

        Response viaDelete = b.delete(path(c));
        assertThat(viaDelete.status()).isEqualTo(200);
        assertThat(viaDelete.quantities()).isEmpty();

        assertThat(b.delete(path(c)).status()).as("deleting a missing line").isEqualTo(200);
        assertThat(b.delete("/api/cart/items/NO-SUCH-SKU").status()).as("deleting an unknown sku").isEqualTo(200);
        assertThat(b.get("/api/cart").num("$.itemCount")).isZero();
    }

    @Test
    void deleteWithNoCartAtAllIsAnEmptyCartAndSetsNoCookie() throws Exception {
        Item item = fixtures().variant(10, 1000);
        CartTestClient b = guest();

        Response r = b.delete(path(item));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.quantities()).isEmpty();
        assertThat(r.setCookie("CART")).isNull();
    }

    @Test
    void guestsNeverSeeEachOthersCarts() throws Exception {
        Item item = fixtures().variant(10, 1000);
        CartTestClient one = guest();
        CartTestClient two = guest();
        one.put(path(item), CartTestClient.qty(4));
        two.put(path(item), CartTestClient.qty(1));

        assertThat(one.get("/api/cart").quantities()).containsEntry(item.sku(), 4);
        assertThat(two.get("/api/cart").quantities()).containsEntry(item.sku(), 1);
        assertThat(one.cookies.get("CART")).isNotEqualTo(two.cookies.get("CART"));
    }

    @Test
    void aForgedOrUnknownCartCookieGetsAFreshCartInsteadOfAnError() throws Exception {
        Item item = fixtures().variant(10, 1000);
        for (String forged : List.of("garbage", "A".repeat(43), "'OR'1'='1'--")) {
            CartTestClient b = guest();
            b.cookies.put("CART", forged);

            Response read = b.get("/api/cart");
            assertThat(read.status()).as("read with %s", forged).isEqualTo(200);
            assertThat(read.quantities()).isEmpty();
            assertThat(read.setCookie("CART")).isNull();

            Response write = b.put(path(item), CartTestClient.qty(1));
            assertThat(write.status()).as("write with %s", forged).isEqualTo(200);
            String issued = b.cookies.get("CART");
            assertThat(issued).as("the forged value is replaced").isNotEqualTo(forged).hasSize(43);
            assertThat(cartsWithTokenHash(issued)).isEqualTo(1);
            assertThat(cartsWithPlainValue(forged)).isZero();
        }
    }

    // ---- validation and stock ---------------------------------------------------------------

    @Test
    void quantityOutsideZeroToTenIsRejectedWithFieldCodes() throws Exception {
        Item item = fixtures().variant(50, 1000);
        CartTestClient b = guest();

        Response tooMany = b.put(path(item), CartTestClient.qty(11));
        Response negative = b.put(path(item), CartTestClient.qty(-1));
        Response missing = b.put(path(item), "{}");
        Response notANumber = b.put(path(item), "{\"quantity\":\"lots\"}");

        assertThat(tooMany.status()).isEqualTo(400);
        assertThat(tooMany.str("$.code")).isEqualTo("validation_failed");
        assertThat(tooMany.str("$.fields.quantity")).isEqualTo("max");
        assertThat(negative.str("$.fields.quantity")).isEqualTo("min");
        assertThat(missing.str("$.fields.quantity")).isEqualTo("required");
        assertThat(notANumber.status()).isEqualTo(400);
        assertThat(notANumber.str("$.code")).isEqualTo("malformed_request");
        assertThat(b.cookies).as("a rejected write must not create a cart").doesNotContainKey("CART");
    }

    @Test
    void tenIsAccepted() throws Exception {
        Item item = fixtures().variant(50, 1000);

        Response r = guest().put(path(item), CartTestClient.qty(10));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.num("$.lines[0].quantity")).isEqualTo(10);
    }

    @Test
    void unknownAndInactiveItemsAreRejected() throws Exception {
        Item inactiveVariant = fixtures().variant(10, 1000);
        fixtures().setVariantActive(inactiveVariant, false);
        Item inactiveProduct = fixtures().variant(10, 1000);
        fixtures().setProductActive(inactiveProduct, false);
        CartTestClient b = guest();

        for (String sku : List.of("NO-SUCH-SKU", inactiveVariant.sku(), inactiveProduct.sku())) {
            Response r = b.put("/api/cart/items/" + sku, CartTestClient.qty(1));
            assertThat(r.status()).as(sku).isEqualTo(404);
            assertThat(r.str("$.code")).as(sku).isEqualTo("unknown_item");
        }
        assertThat(b.put("/api/cart/items/" + "x".repeat(101), CartTestClient.qty(1)).status()).isEqualTo(404);
        assertThat(b.cookies).doesNotContainKey("CART");
    }

    @Test
    void askingForMoreThanIsInStockIsA409AndAddsNothing() throws Exception {
        Item item = fixtures().variant(1, 1000);
        CartTestClient b = guest();

        Response r = b.put(path(item), CartTestClient.qty(2));

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.str("$.code")).isEqualTo("insufficient_stock");
        assertThat(r.str("$.fields.quantity")).isEqualTo("stock");
        assertThat(b.cookies).doesNotContainKey("CART");
        assertThat(b.put(path(item), CartTestClient.qty(1)).status()).isEqualTo(200);
    }

    @Test
    void linesShowLiveStockPriceAndAvailabilityWithoutReservingAnything() throws Exception {
        Item item = fixtures().variant(5, 12990);
        CartTestClient b = guest();
        b.put(path(item), CartTestClient.qty(3));

        fixtures().setStock(item, 2);
        Response lowStock = b.get("/api/cart");
        assertThat(lowStock.bool("$.lines[0].available")).isFalse();
        assertThat(lowStock.num("$.lines[0].maxQuantity")).isEqualTo(2);

        fixtures().setStock(item, 40);
        fixtures().setPrice(item, 9990);
        Response repriced = b.get("/api/cart");
        assertThat(repriced.bool("$.lines[0].available")).isTrue();
        assertThat(repriced.num("$.lines[0].maxQuantity")).as("capped at 10").isEqualTo(10);
        assertThat(repriced.num("$.lines[0].unitPriceMinor")).isEqualTo(9990);
        assertThat(repriced.num("$.lines[0].lineTotalMinor")).isEqualTo(29970);

        fixtures().setVariantActive(item, false);
        Response variantOff = b.get("/api/cart");
        assertThat(variantOff.bool("$.lines[0].available")).isFalse();
        assertThat(variantOff.num("$.lines[0].maxQuantity")).isZero();
        assertThat(variantOff.num("$.subtotalMinor")).as("unavailable lines still count; checkout enforces").isEqualTo(29970);

        fixtures().setVariantActive(item, true);
        fixtures().setProductActive(item, false);
        assertThat(b.get("/api/cart").bool("$.lines[0].available")).isFalse();
        fixtures().setProductActive(item, true);

        long stock = jdbc.sql("SELECT stock FROM variants WHERE id = :id").param("id", item.id()).query(Long.class).single();
        assertThat(stock).as("the cart must not take stock").isEqualTo(40);
    }

    // ---- CSRF -------------------------------------------------------------------------------

    @Test
    void writesWithoutACsrfTokenAreRejectedAndCreateNoCart() throws Exception {
        Item item = fixtures().variant(10, 1000);
        CartTestClient b = guest();
        long before = allCarts();

        Response put = b.putWithoutCsrf(path(item), CartTestClient.qty(1));
        Response delete = b.deleteWithoutCsrf(path(item));

        for (Response r : List.of(put, delete)) {
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.str("$.code")).isEqualTo("csrf_invalid");
        }
        assertThat(allCarts()).isEqualTo(before);
        assertThat(b.cookies).doesNotContainKey("CART");
    }

    // ---- concurrency ------------------------------------------------------------------------

    @Test
    void concurrentUpdatesToOneLineLeaveExactlyOneRowWithAValidQuantity() throws Exception {
        Item item = fixtures().variant(100, 1000);
        CartTestClient b = guest();
        b.put(path(item), CartTestClient.qty(1)); // the cart exists before the race (see docs/api/cart.md)
        String token = b.cookies.get("CART");

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int quantity = 1 + (i % 10);
            results.add(pool.submit(() -> b.put(path(item), CartTestClient.qty(quantity)).status()));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }

        assertThat(itemRowsForGuest(token)).isEqualTo(1);
        int quantity = b.get("/api/cart").quantities().get(item.sku());
        assertThat(quantity).isBetween(1, 10);
    }

    // ---- signed-in carts --------------------------------------------------------------------

    @Test
    void aSignedInCartBelongsToTheCustomerSetsNoCartCookieAndFollowsThemToAnotherBrowser() throws Exception {
        Item item = fixtures().variant(10, 1000);
        String email = uniqueEmail();
        CartTestClient first = new CartTestClient(base());
        assertThat(first.register(email, PASSWORD).status()).isEqualTo(201);

        Response write = first.put(path(item), CartTestClient.qty(3));

        assertThat(write.status()).isEqualTo(200);
        assertThat(write.setCookie("CART")).as("customers need no cart cookie").isNull();
        assertThat(customerCartItems(email)).isEqualTo(1);

        CartTestClient second = new CartTestClient(base());
        assertThat(second.login(email, PASSWORD).status()).isEqualTo(200);
        assertThat(second.get("/api/cart").quantities()).containsEntry(item.sku(), 3);
    }

    // ---- merge on sign-in -------------------------------------------------------------------

    @Test
    void loggingInMergesTheGuestCartKeepingTheLargerQuantityAndRetiresTheCookie() throws Exception {
        Item a = fixtures().variant(50, 1000);
        Item b = fixtures().variant(50, 2000);
        Item c = fixtures().variant(50, 3000);
        String email = uniqueEmail();

        CartTestClient earlier = new CartTestClient(base());
        earlier.register(email, PASSWORD);
        earlier.put(path(a), CartTestClient.qty(5));
        earlier.put(path(c), CartTestClient.qty(1));

        CartTestClient browser = guest();
        browser.put(path(a), CartTestClient.qty(7));
        browser.put(path(b), CartTestClient.qty(2));
        String token = browser.cookies.get("CART");
        assertThat(cartsWithTokenHash(token)).isEqualTo(1);

        Response login = browser.login(email, PASSWORD);

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.setCookie("CART")).as("login must retire the guest cookie: %s", login.headers().allValues("set-cookie"))
                .isNotNull();
        assertThat(CartTestClient.attributes(login.setCookie("CART"))).contains("max-age=0");
        assertThat(browser.cookies).doesNotContainKey("CART");
        assertThat(cartsWithTokenHash(token)).as("the guest cart is gone").isZero();

        Response merged = browser.get("/api/cart");
        assertThat(merged.quantities()).containsOnly(
                Map.entry(a.sku(), 7), Map.entry(b.sku(), 2), Map.entry(c.sku(), 1));
        assertThat(merged.num("$.itemCount")).isEqualTo(10);
        assertThat(customerCartItems(email)).isEqualTo(3);
    }

    @Test
    void theLargerQuantityWinsInBothDirectionsAndReplayingTheOldCookieInflatesNothing() throws Exception {
        Item item = fixtures().variant(50, 1000);

        // guest 3 < customer 5: the customer's 5 stays
        String lowGuest = uniqueEmail();
        CartTestClient owner = new CartTestClient(base());
        owner.register(lowGuest, PASSWORD);
        owner.put(path(item), CartTestClient.qty(5));
        CartTestClient browser = guest();
        browser.put(path(item), CartTestClient.qty(3));
        String oldCookie = browser.cookies.get("CART");
        browser.login(lowGuest, PASSWORD);
        assertThat(browser.get("/api/cart").quantities()).containsEntry(item.sku(), 5);

        // replaying the already-merged cookie from another browser changes nothing
        CartTestClient replay = new CartTestClient(base());
        replay.cookies.put("CART", oldCookie);
        replay.login(lowGuest, PASSWORD);
        assertThat(replay.get("/api/cart").quantities()).containsEntry(item.sku(), 5);

        // guest 10 > customer 5: the guest's 10 wins, never above the cap
        String highGuest = uniqueEmail();
        CartTestClient owner2 = new CartTestClient(base());
        owner2.register(highGuest, PASSWORD);
        owner2.put(path(item), CartTestClient.qty(5));
        CartTestClient browser2 = guest();
        browser2.put(path(item), CartTestClient.qty(10));
        browser2.login(highGuest, PASSWORD);
        assertThat(browser2.get("/api/cart").quantities()).containsEntry(item.sku(), 10);
    }

    @Test
    void registeringCarriesTheGuestCartOverToTheNewAccount() throws Exception {
        Item item = fixtures().variant(10, 1000);
        String email = uniqueEmail();
        CartTestClient browser = guest();
        browser.put(path(item), CartTestClient.qty(2));
        String token = browser.cookies.get("CART");

        Response register = browser.register(email, PASSWORD);

        assertThat(register.status()).isEqualTo(201);
        assertThat(browser.cookies).doesNotContainKey("CART");
        assertThat(cartsWithTokenHash(token)).isZero();
        assertThat(browser.get("/api/cart").quantities()).containsOnly(Map.entry(item.sku(), 2));
        assertThat(customerCartItems(email)).isEqualTo(1);
    }

    @Test
    void aStaleCartCookieAtLoginIsHarmlessAndRetired() throws Exception {
        String email = uniqueEmail();
        CartTestClient setup = new CartTestClient(base());
        setup.register(email, PASSWORD);
        CartTestClient browser = new CartTestClient(base());
        browser.cookies.put("CART", "A".repeat(43)); // well-formed, matches no cart

        Response login = browser.login(email, PASSWORD);

        assertThat(login.status()).isEqualTo(200);
        assertThat(browser.cookies).doesNotContainKey("CART");
        assertThat(browser.get("/api/cart").quantities()).isEmpty();
    }
}
