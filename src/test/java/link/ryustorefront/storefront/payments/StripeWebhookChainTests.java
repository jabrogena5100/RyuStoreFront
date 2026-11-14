package link.ryustorefront.storefront.payments;

import static link.ryustorefront.storefront.payments.WebhookFixtures.completed;
import static link.ryustorefront.storefront.payments.WebhookFixtures.eventId;
import static link.ryustorefront.storefront.payments.WebhookFixtures.paymentIntent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.BooleanSupplier;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.cart.CartTestClient;
import link.ryustorefront.storefront.notifications.MailpitConfiguration;
import link.ryustorefront.storefront.notifications.MailpitContainer;
import link.ryustorefront.storefront.outbox.OutboxProcessor;
import link.ryustorefront.storefront.outbox.OutboxProcessor.Drained;
import link.ryustorefront.storefront.payments.WebhookFixtures.Placed;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The whole purchase, end to end, with everything real except Stripe itself: a shopper fills a cart, checks
 * out and starts payment (fake gateway), a signed webhook arrives over HTTP, the real outbox processor
 * drains {@code order.paid} to both handlers, the confirmation goes through real SMTP into Mailpit, and the
 * cart loses exactly the purchased lines.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stripe.webhook-secret=" + WebhookFixtures.SECRET)
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StripeWebhookChainTests {

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    OutboxProcessor processor;
    @Autowired
    MailpitContainer mailpit;

    WebhookFixtures fx;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    void setUp() {
        fx = new WebhookFixtures(jdbc, "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class));
    }

    @BeforeEach
    void reset() throws Exception {
        FakePaymentGateway.reset();
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        mailpit("DELETE", "/api/v1/messages");
    }

    @AfterAll
    void cleanUp() {
        fx.cleanUp();
    }

    private String mailpit(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(mailpit.apiBase() + path))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s -> %s", method, path, response.body()).isBetween(200, 299);
        return response.body();
    }

    private int inboxSize() {
        try {
            List<?> messages = JsonPath.read(mailpit("GET", "/api/v1/messages"), "$.messages");
            return messages.size();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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

    @Test
    void payingThroughTheWebhookSendsOneEmailAndEmptiesOnlyThePurchasedLinesFromTheCart() throws Exception {
        Placed order = fx.payablePendingOrder();
        CartTestClient browser = order.shopper().browser();
        assertThat(browser.get("/api/cart").quantities()).as("checkout alone does not empty the cart")
                .containsOnlyKeys(order.sku());
        // Added after checkout, so not part of the order: it must survive.
        String laterSku = fx.variantSku();
        assertThat(browser.put("/api/cart/items/" + laterSku, CartTestClient.qty(1)).status()).isEqualTo(200);
        assertThat(browser.get("/api/cart").quantities()).containsOnlyKeys(order.sku(), laterSku);

        String body = completed(eventId(), order, paymentIntent());
        assertThat(fx.deliver(body).status()).isEqualTo(200);
        assertThat(fx.status(order.orderId())).isEqualTo("paid");
        assertThat(browser.get("/api/cart").quantities()).as("nothing is emptied until the worker runs")
                .containsOnlyKeys(order.sku(), laterSku);
        assertThat(inboxSize()).isZero();

        Drained drained = processor.drain(20);

        assertThat(drained.failed()).isZero();
        assertThat(drained.processed()).as("one order.paid event, two handlers").isEqualTo(1);
        assertThat(eventually(() -> inboxSize() == 1)).as("one message in Mailpit").isTrue();
        String list = mailpit("GET", "/api/v1/messages");
        assertThat(JsonPath.<String>read(list, "$.messages[0].To[0].Address")).isEqualTo(order.shopper().email());
        String number = order.orderId().toString().substring(0, 8).toUpperCase();
        assertThat(JsonPath.<String>read(list, "$.messages[0].Subject")).isEqualTo("Your order " + number + " is confirmed");
        assertThat(browser.get("/api/cart").quantities()).as("the purchased line is gone, the later one stays")
                .containsOnlyKeys(laterSku);

        // Stripe delivers the same event again, and the worker runs again: still one email, nothing else changes.
        assertThat(fx.deliver(body).status()).isEqualTo(200);
        assertThat(processor.drain(20)).isEqualTo(new Drained(0, 0));
        Thread.sleep(500);
        assertThat(inboxSize()).isEqualTo(1);
        assertThat(fx.count("SELECT count(*) FROM sent_emails")).isEqualTo(1);
    }

    @Test
    void aShopperWhoDoesNotPayKeepsTheirCartAndGetsNoEmail() throws Exception {
        Placed order = fx.payablePendingOrder();

        String expired = WebhookFixtures.expired(eventId(), order);
        assertThat(fx.deliver(expired).status()).isEqualTo(200);
        assertThat(processor.drain(20).failed()).isZero();

        assertThat(fx.status(order.orderId())).isEqualTo("cancelled");
        assertThat(order.shopper().browser().get("/api/cart").quantities()).containsOnlyKeys(order.sku());
        Thread.sleep(500);
        assertThat(inboxSize()).isZero();
        assertThat(fx.count("SELECT count(*) FROM sent_emails")).isZero();
    }
}
