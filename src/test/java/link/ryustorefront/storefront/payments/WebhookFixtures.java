package link.ryustorefront.storefront.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import link.ryustorefront.storefront.cart.CartTestClient;
import link.ryustorefront.storefront.cart.CartTestClient.Response;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Shared setup for the Stripe webhook tests: real shoppers and orders made through the real HTTP API, and
 * webhook requests signed by an independent implementation of Stripe's scheme (HMAC-SHA256 over
 * {@code "<timestamp>.<payload>"}, written here from the documented format, not by calling the SDK).
 */
final class WebhookFixtures {

    /** The signing secret the webhook tests configure (property {@code stripe.webhook-secret}). */
    static final String SECRET = "whsec_test_only_not_a_real_secret";
    static final String PASSWORD = "correct horse battery";
    /** 2 x 129.90 USD of goods plus the flat 8.00 shipping. */
    static final long TOTAL = 26_780;
    private static final String ADDRESS = """
            {"name":"Ada Buyer","line1":"1 Main St","city":"Portland","region":"OR","postalCode":"97201","country":"US"}""";

    record Shopper(CartTestClient browser, String email, long id) {
    }

    /** A pending order and the session its payment page was created with (empty until {@link #startPayment}). */
    record Placed(Shopper shopper, UUID orderId, String sku, String sessionId) {
    }

    record Reply(int status, String body) {
    }

    private final JdbcClient jdbc;
    private final String base;
    private final HttpClient http = HttpClient.newHttpClient();

    WebhookFixtures(JdbcClient jdbc, String base) {
        this.jdbc = jdbc;
        this.base = base;
    }

    // ---- shoppers and orders ------------------------------------------------------------------

    Shopper signedIn() throws Exception {
        String email = "wh-test-" + UUID.randomUUID() + "@example.com";
        CartTestClient browser = new CartTestClient(base);
        assertThat(browser.register(email, PASSWORD).status()).isEqualTo(201);
        long id = jdbc.sql("SELECT id FROM customers WHERE email = :e").param("e", email).query(Long.class).single();
        return new Shopper(browser, email, id);
    }

    /** A variant with 20 in stock at 129.90 USD. */
    String variantSku() {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'Test') RETURNING id")
                .param("s", "wh-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("INSERT INTO products (slug, name, brand, category_id, gender) "
                        + "VALUES (:s, 'Webhook test boot', 'Test', :c, 'unisex') RETURNING id")
                .param("s", "wh-test-p-" + id).param("c", category).query(Long.class).single();
        String sku = "WHTEST-" + id;
        jdbc.sql("INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock) "
                        + "VALUES (:p, :sku, '42', 'black', 12990, 20)").param("p", product).param("sku", sku).update();
        return sku;
    }

    /** A signed-in shopper with a real pending order (cart, then POST /api/checkout) of 2 x 129.90 + shipping. */
    Placed pendingOrder() throws Exception {
        Shopper shopper = signedIn();
        String sku = variantSku();
        assertThat(shopper.browser().put("/api/cart/items/" + sku, CartTestClient.qty(2)).status()).isEqualTo(200);
        Response checkout = shopper.browser().post("/api/checkout",
                "{\"shippingAddress\":" + ADDRESS + "}", Map.of("Idempotency-Key", UUID.randomUUID().toString()));
        assertThat(checkout.status()).isEqualTo(201);
        UUID orderId = UUID.fromString(checkout.str("$.id"));
        assertThat(jdbc.sql("SELECT total_minor FROM orders WHERE id = :id").param("id", orderId)
                .query(Long.class).single()).as("the fixture's price arithmetic").isEqualTo(TOTAL);
        return new Placed(shopper, orderId, sku, null);
    }

    /** POST /api/orders/{id}/pay (the fake gateway answers) and read the session id it stored. */
    Placed startPayment(Placed order) throws Exception {
        Response pay = order.shopper().browser().post("/api/orders/" + order.orderId() + "/pay", null, Map.of());
        assertThat(pay.status()).isEqualTo(200);
        String session = jdbc.sql("SELECT stripe_checkout_session_id FROM orders WHERE id = :id")
                .param("id", order.orderId()).query(String.class).single();
        return new Placed(order.shopper(), order.orderId(), order.sku(), session);
    }

    Placed payablePendingOrder() throws Exception {
        return startPayment(pendingOrder());
    }

    // ---- database oracles ---------------------------------------------------------------------

    String status(UUID orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id").param("id", orderId).query(String.class).single();
    }

    long count(String sql, Object... params) {
        JdbcClient.StatementSpec spec = jdbc.sql(sql);
        for (Object p : params) {
            spec = spec.param(p);
        }
        return spec.query(Long.class).single();
    }

    long payments(UUID orderId) {
        return count("SELECT count(*) FROM payments WHERE order_id = ?", orderId);
    }

    long outboxEvents(UUID orderId, String type) {
        return count("SELECT count(*) FROM outbox WHERE aggregate_id = ? AND event_type = ?", orderId.toString(), type);
    }

    long stock(String sku) {
        return count("SELECT stock FROM variants WHERE sku = ?", sku);
    }

    String reservationStatus(UUID orderId) {
        return jdbc.sql("SELECT status FROM reservations WHERE order_id = :id").param("id", orderId)
                .query(String.class).single();
    }

    long recordedEvents(String eventId) {
        return count("SELECT count(*) FROM stripe_events WHERE event_id = ?", eventId);
    }

    // ---- webhook requests ---------------------------------------------------------------------

    static String eventId() {
        return "evt_whtest_" + UUID.randomUUID().toString().replace("-", "");
    }

    static String paymentIntent() {
        return "pi_whtest_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * A checkout.session event shaped like Stripe's, with an API version far in the future: the handler
     * must not depend on the SDK's typed event classes. {@code paymentIntent} may be null (absent).
     */
    static String sessionEvent(String eventId, String type, String sessionId, Object orderId, String paymentStatus,
                               String paymentIntent, long amountTotal, String currency) {
        String intent = paymentIntent == null ? "" : "\"payment_intent\": \"" + paymentIntent + "\",\n";
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "api_version": "2099-01-01.future",
                  "created": 1790000000,
                  "livemode": false,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "checkout.session",
                      "client_reference_id": "%s",
                      "metadata": {"order_id": "%s"},
                      "payment_status": "%s",
                      %s"amount_total": %d,
                      "currency": "%s",
                      "customer_email": "buyer-é@example.com"
                    }
                  }
                }""".formatted(eventId, type, sessionId, orderId, orderId, paymentStatus, intent, amountTotal, currency);
    }

    static String completed(String eventId, Placed order, String intent) {
        return sessionEvent(eventId, "checkout.session.completed", order.sessionId(), order.orderId(), "paid", intent,
                TOTAL, "usd");
    }

    static String expired(String eventId, Placed order) {
        return sessionEvent(eventId, "checkout.session.expired", order.sessionId(), order.orderId(), "unpaid", null,
                TOTAL, "usd");
    }

    /** Stripe's header: {@code t=<unix seconds>,v1=<hex HMAC-SHA256 of "t.payload">}. */
    static String signatureHeader(String payload, String secret, long timestampSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestampSeconds + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestampSeconds + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String signatureHeader(String payload) {
        return signatureHeader(payload, SECRET, System.currentTimeMillis() / 1000);
    }

    /** Sends the payload with a correct, current signature. */
    Reply deliver(String payload) throws Exception {
        return post(payload, signatureHeader(payload));
    }

    /** Sends the payload with exactly this header (null: no header at all). */
    Reply post(String payload, String signatureHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/api/stripe/webhook"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
        if (signatureHeader != null) {
            request.header("Stripe-Signature", signatureHeader);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    // ---- cleanup --------------------------------------------------------------------------------

    void cleanUp() {
        String mine = "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id WHERE c.email LIKE 'wh-test-%'";
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        jdbc.sql("DELETE FROM stripe_events WHERE event_id LIKE 'evt_whtest_%'").update();
        jdbc.sql("DELETE FROM payments WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM reservations WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_items WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE email LIKE 'wh-test-%')").update();
        jdbc.sql("DELETE FROM cart_items WHERE variant_id IN (SELECT id FROM variants WHERE sku LIKE 'WHTEST-%')").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'WHTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'wh-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'wh-test-c-%'").update();
        jdbc.sql("DELETE FROM customers WHERE email LIKE 'wh-test-%'").update();
    }
}
