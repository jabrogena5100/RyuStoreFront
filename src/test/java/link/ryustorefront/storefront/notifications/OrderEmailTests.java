package link.ryustorefront.storefront.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.orders.NewOrder;
import link.ryustorefront.storefront.orders.OrderStatus;
import link.ryustorefront.storefront.orders.Orders;
import link.ryustorefront.storefront.orders.ShippingAddress;
import link.ryustorefront.storefront.outbox.OutboxProcessor;
import link.ryustorefront.storefront.outbox.OutboxProcessor.Drained;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The whole chain with a real mail server: an order is paid, the outbox event is drained by the real
 * processor, the real handler sends through real SMTP, and the message is read back from Mailpit's
 * inbox. Includes redelivery (no second email) and a mail-server outage (retried, then one email).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderEmailTests {

    @Autowired
    Orders orders;
    @Autowired
    OutboxProcessor processor;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    MailpitContainer mailpit;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() throws Exception {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        call("DELETE", "/api/v1/messages");
    }

    @AfterAll
    void cleanUp() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        String mine = "SELECT id FROM orders WHERE contact_email LIKE 'mail-test-%'";
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_items WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE contact_email LIKE 'mail-test-%'").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'MAILTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'mail-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'mail-test-c-%'").update();
        jdbc.sql("DELETE FROM customers WHERE email LIKE 'mail-test-%'").update();
    }

    // ---- fixtures ---------------------------------------------------------------------------

    private record Placed(UUID id, String email) {
    }

    private long variant() {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'Test') RETURNING id")
                .param("s", "mail-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("INSERT INTO products (slug, name, brand, category_id, gender) "
                        + "VALUES (:s, 'Mail test', 'Test', :c, 'unisex') RETURNING id")
                .param("s", "mail-test-p-" + id).param("c", category).query(Long.class).single();
        return jdbc.sql("INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock) "
                        + "VALUES (:p, :sku, '42', 'black', 12990, 5) RETURNING id")
                .param("p", product).param("sku", "MAILTEST-" + id).query(Long.class).single();
    }

    /** A pending order with a snapshot of two lines, totalling 317.80 USD. */
    private Placed pendingOrder() {
        String email = "mail-test-" + UUID.randomUUID() + "@example.com";
        long customer = jdbc.sql("INSERT INTO customers (email, password_hash) VALUES (:e, 'unused') RETURNING id")
                .param("e", email).query(Long.class).single();
        UUID id = orders.createPending(new NewOrder(customer, email, "USD", 30_980, 800,
                new ShippingAddress("Ada <Buyer> & Co", "1 Main St", "Apt 4", "Portland", "OR", "97201", "US"),
                UUID.randomUUID().toString(),
                List.of(new NewOrder.Item(variant(), "MAILTEST-A", "Boot <b>X</b> & Co", "42", "black", 12_990, 2),
                        new NewOrder.Item(variant(), "MAILTEST-B", "Plain Sneaker", "41", "white", 5_000, 1))));
        return new Placed(id, email);
    }

    // ---- Mailpit helpers --------------------------------------------------------------------

    private String call(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(mailpit.apiBase() + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s -> %s", method, path, response.body()).isBetween(200, 299);
        return response.body();
    }

    private List<Map<String, Object>> inbox() throws Exception {
        return JsonPath.read(call("GET", "/api/v1/messages"), "$.messages");
    }

    private boolean eventually(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 40; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(250);
        }
        return condition.getAsBoolean();
    }

    private int inboxSize() {
        try {
            return inbox().size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long sentRows() {
        return jdbc.sql("SELECT count(*) FROM sent_emails").query(Long.class).single();
    }

    // ---- tests ------------------------------------------------------------------------------

    @Test
    void payingAnOrderSendsOneConfirmationWithItemsTotalsAndAddress() throws Exception {
        Placed order = pendingOrder();

        assertThat(orders.transition(order.id(), OrderStatus.PAID, "test").applied()).isTrue();
        assertThat(processor.drain(10)).isEqualTo(new Drained(1, 0));

        assertThat(eventually(() -> inboxSize() == 1)).as("one message in the inbox").isTrue();
        Map<String, Object> summary = inbox().get(0);
        String message = call("GET", "/api/v1/message/" + summary.get("ID"));
        String number = order.id().toString().substring(0, 8).toUpperCase();
        assertThat(JsonPath.<String>read(message, "$.Subject")).isEqualTo("Your order " + number + " is confirmed");
        assertThat(JsonPath.<String>read(message, "$.To[0].Address")).isEqualTo(order.email());
        assertThat(JsonPath.<String>read(message, "$.From.Address")).isEqualTo("orders@storefront.local");
        String text = JsonPath.read(message, "$.Text");
        assertThat(text).contains("2 x Boot <b>X</b> & Co (size 42, black)")
                .contains("259.80 USD")
                .contains("1 x Plain Sneaker (size 41, white)")
                .contains("Total:    317.80 USD")
                .contains("Ada <Buyer> & Co")
                .contains("Portland, OR 97201");
        String html = JsonPath.read(message, "$.HTML");
        assertThat(html).contains("Boot &lt;b&gt;X&lt;/b&gt; &amp; Co").doesNotContain("<b>X</b>")
                .contains("Ada &lt;Buyer&gt; &amp; Co");
        assertThat(jdbc.sql("SELECT recipient FROM sent_emails WHERE dedupe_key = :k")
                .param("k", "order.paid:" + order.id()).query(String.class).single()).isEqualTo(order.email());
    }

    @Test
    void anEventDeliveredASecondTimeSendsNoSecondEmail() throws Exception {
        Placed order = pendingOrder();
        orders.transition(order.id(), OrderStatus.PAID, "test");
        processor.drain(10);
        assertThat(eventually(() -> inboxSize() == 1)).isTrue();

        // Simulate the outbox delivering the same event again (what a crash before commit would cause).
        jdbc.sql("UPDATE outbox SET processed_at = NULL, available_at = now() WHERE aggregate_id = :id")
                .param("id", order.id().toString()).update();
        Drained again = processor.drain(10);

        assertThat(again).isEqualTo(new Drained(1, 0));
        Thread.sleep(1_000);
        assertThat(inboxSize()).as("still exactly one email").isEqualTo(1);
        assertThat(sentRows()).isEqualTo(1);
    }

    @Test
    void aMailServerOutageIsRetriedAndExactlyOneEmailArrivesOnceItIsBack() throws Exception {
        Placed order = pendingOrder();
        orders.transition(order.id(), OrderStatus.PAID, "test");
        mailpit.getDockerClient().pauseContainerCmd(mailpit.getContainerId()).exec();
        try {
            Drained down = processor.drain(10);

            assertThat(down).as("the send failed").isEqualTo(new Drained(0, 1));
            assertThat(sentRows()).as("the failed attempt's ledger row was rolled back").isZero();
            assertThat(jdbc.sql("SELECT attempts FROM outbox WHERE aggregate_id = :id")
                    .param("id", order.id().toString()).query(Integer.class).single()).isEqualTo(1);
            assertThat(jdbc.sql("SELECT last_error FROM outbox WHERE aggregate_id = :id")
                    .param("id", order.id().toString()).query(String.class).single()).isNotBlank();
        } finally {
            mailpit.getDockerClient().unpauseContainerCmd(mailpit.getContainerId()).exec();
        }

        jdbc.sql("UPDATE outbox SET available_at = now() WHERE aggregate_id = :id")
                .param("id", order.id().toString()).update();
        assertThat(eventually(() -> processor.drain(10).processed() == 1))
                .as("the retry succeeds once the server answers").isTrue();

        assertThat(eventually(() -> inboxSize() == 1)).isTrue();
        assertThat(sentRows()).isEqualTo(1);
    }

    @Test
    void ordersThatAreCancelledSendNoConfirmation() throws Exception {
        Placed order = pendingOrder();
        orders.transition(order.id(), OrderStatus.CANCELLED, "test");

        Drained drained = processor.drain(10);

        assertThat(drained).isEqualTo(new Drained(1, 0));
        Thread.sleep(500);
        assertThat(inboxSize()).isZero();
        assertThat(sentRows()).isZero();
    }
}
