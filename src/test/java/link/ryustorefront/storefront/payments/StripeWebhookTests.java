package link.ryustorefront.storefront.payments;

import static link.ryustorefront.storefront.payments.WebhookFixtures.TOTAL;
import static link.ryustorefront.storefront.payments.WebhookFixtures.completed;
import static link.ryustorefront.storefront.payments.WebhookFixtures.eventId;
import static link.ryustorefront.storefront.payments.WebhookFixtures.expired;
import static link.ryustorefront.storefront.payments.WebhookFixtures.paymentIntent;
import static link.ryustorefront.storefront.payments.WebhookFixtures.sessionEvent;
import static link.ryustorefront.storefront.payments.WebhookFixtures.signatureHeader;
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
import link.ryustorefront.storefront.notifications.MailpitConfiguration;
import link.ryustorefront.storefront.outbox.OutboxProcessor;
import link.ryustorefront.storefront.payments.WebhookFixtures.Placed;
import link.ryustorefront.storefront.payments.WebhookFixtures.Reply;
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
 * The Stripe webhook against the real server and Postgres: signature checking on the raw body, idempotency
 * under replay and concurrency, and events arriving out of order. Requests are signed by an independent
 * HMAC in {@link WebhookFixtures}; every claim is checked in the JSON answer and again in the database rows.
 *
 * <p>Mailpit is imported only so draining the outbox (which delivers {@code order.paid} to the email handler)
 * has a mail server; the same imports and properties as the chain test share one application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "stripe.webhook-secret=" + WebhookFixtures.SECRET)
@Import({TestcontainersConfiguration.class, MailpitConfiguration.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StripeWebhookTests {

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    OutboxProcessor processor;

    WebhookFixtures fx;

    @BeforeAll
    void setUp() {
        fx = new WebhookFixtures(jdbc, "http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class));
    }

    @BeforeEach
    void reset() {
        FakePaymentGateway.reset();
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
    }

    @AfterAll
    void cleanUp() {
        fx.cleanUp();
    }

    // ---- signatures -------------------------------------------------------------------------------

    @Test
    void aCorrectlySignedEventForAnApiVersionTheSdkNeverHeardOfMarksTheOrderPaid() throws Exception {
        Placed order = fx.payablePendingOrder();
        String id = eventId();

        Reply reply = fx.deliver(completed(id, order, paymentIntent()));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(fx.status(order.orderId())).isEqualTo("paid");
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("committed");
        assertThat(fx.stock(order.sku())).as("20 minus the 2 bought, still taken").isEqualTo(18);
        assertThat(fx.recordedEvents(id)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT processed_at IS NOT NULL FROM stripe_events WHERE event_id = :id")
                .param("id", id).query(Boolean.class).single()).isTrue();
        assertThat(jdbc.sql("SELECT amount_minor || ' ' || currency || ' ' || status FROM payments WHERE order_id = :id")
                .param("id", order.orderId()).query(String.class).single()).isEqualTo(TOTAL + " USD succeeded");
    }

    @Test
    void aBodyChangedAfterSigningIsRejectedAndChangesNothing() throws Exception {
        Placed order = fx.payablePendingOrder();
        String id = eventId();
        String signedBody = completed(id, order, paymentIntent());
        String header = signatureHeader(signedBody);
        String tampered = signedBody.replace("\"amount_total\": " + TOTAL, "\"amount_total\": 1");
        assertThat(tampered).isNotEqualTo(signedBody);

        Reply reply = fx.post(tampered, header);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body()).contains("invalid_signature");
        assertThat(fx.status(order.orderId())).isEqualTo("pending");
        assertThat(fx.recordedEvents(id)).isZero();
        assertThat(fx.payments(order.orderId())).isZero();
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRejected() throws Exception {
        Placed order = fx.payablePendingOrder();
        String body = completed(eventId(), order, paymentIntent());

        Reply reply = fx.post(body, signatureHeader(body, "whsec_some_other_secret", System.currentTimeMillis() / 1000));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(fx.status(order.orderId())).isEqualTo("pending");
    }

    @Test
    void aStaleTimestampIsRejectedEvenWithAValidMacAndAFreshOneJustInsideTheToleranceIsAccepted() throws Exception {
        Placed order = fx.payablePendingOrder();
        long now = System.currentTimeMillis() / 1000;
        String body = completed(eventId(), order, paymentIntent());

        Reply stale = fx.post(body, signatureHeader(body, WebhookFixtures.SECRET, now - 10 * 60));
        assertThat(stale.status()).as("ten minutes old, tolerance is five").isEqualTo(400);
        assertThat(fx.status(order.orderId())).isEqualTo("pending");

        // An event type we ignore, so this proves only the tolerance boundary and has no side effects.
        String ping = "{\"id\":\"" + eventId() + "\",\"type\":\"customer.created\",\"data\":{\"object\":{}}}";
        Reply recent = fx.post(ping, signatureHeader(ping, WebhookFixtures.SECRET, now - 4 * 60));
        assertThat(recent.status()).as("four minutes old is inside the tolerance").isEqualTo(200);
    }

    @Test
    void aMissingOrMalformedSignatureHeaderIsRejected() throws Exception {
        Placed order = fx.payablePendingOrder();
        String body = completed(eventId(), order, paymentIntent());

        assertThat(fx.post(body, null).status()).as("no header").isEqualTo(400);
        assertThat(fx.post(body, "").status()).as("empty header").isEqualTo(400);
        assertThat(fx.post(body, "garbage").status()).as("not t=,v1=").isEqualTo(400);
        assertThat(fx.post(body, "t=" + System.currentTimeMillis() / 1000).status()).as("no v1").isEqualTo(400);
        assertThat(fx.post(body, "t=abc,v1=00").status()).as("timestamp is not a number").isEqualTo(400);
        assertThat(fx.status(order.orderId())).isEqualTo("pending");
    }

    @Test
    void theSignatureCoversTheExactBytesSoWhitespaceAndNonAsciiTextAreKept() throws Exception {
        // sessionEvent is pretty-printed and carries a non-ASCII e-mail address; it is signed exactly as sent.
        Placed order = fx.payablePendingOrder();
        String body = completed(eventId(), order, paymentIntent());
        assertThat(body).contains("\n").contains("é");

        assertThat(fx.deliver(body).status()).isEqualTo(200);

        // The same JSON with its whitespace squeezed out is different bytes, so the old signature fails.
        Placed other = fx.payablePendingOrder();
        String pretty = completed(eventId(), other, paymentIntent());
        String squeezed = pretty.replaceAll("\\s*\\n\\s*", "");
        assertThat(squeezed).isNotEqualTo(pretty);
        assertThat(fx.post(squeezed, signatureHeader(pretty)).status()).isEqualTo(400);
        assertThat(fx.status(other.orderId())).isEqualTo("pending");
    }

    // ---- idempotency ------------------------------------------------------------------------------

    @Test
    void theSameEventDeliveredThreeTimesPaysOnceAndSendsOneEmail() throws Exception {
        Placed order = fx.payablePendingOrder();
        String id = eventId();
        String body = completed(id, order, paymentIntent());

        for (int i = 0; i < 3; i++) {
            assertThat(fx.deliver(body).status()).as("delivery %d", i + 1).isEqualTo(200);
        }

        assertOnePayment(order);
        assertThat(fx.recordedEvents(id)).isEqualTo(1);
        assertThat(processor.drain(50).failed()).isZero();
        assertThat(fx.count("SELECT count(*) FROM sent_emails WHERE dedupe_key = ?", "order.paid:" + order.orderId()))
                .as("one confirmation").isEqualTo(1);
    }

    @Test
    void theSameEventDeliveredAtTheSameMomentPaysOnce() throws Exception {
        Placed order = fx.payablePendingOrder();
        String body = completed(eventId(), order, paymentIntent());

        List<Integer> statuses = concurrently(6, i -> fx.deliver(body).status());

        assertThat(statuses).as("every delivery is answered 200, none fails").containsOnly(200);
        assertOnePayment(order);
        assertThat(processor.drain(50).failed()).isZero();
        assertThat(fx.count("SELECT count(*) FROM sent_emails WHERE dedupe_key = ?", "order.paid:" + order.orderId()))
                .isEqualTo(1);
    }

    @Test
    void twoDifferentEventsForTheSamePaymentRacingEachOtherStillPayOnce() throws Exception {
        // Stripe can send checkout.session.completed and ...async_payment_succeeded for one session.
        Placed order = fx.payablePendingOrder();
        String intent = paymentIntent();
        String completed = completed(eventId(), order, intent);
        String succeeded = sessionEvent(eventId(), "checkout.session.async_payment_succeeded", order.sessionId(),
                order.orderId(), "paid", intent, TOTAL, "usd");

        List<Integer> statuses = concurrently(6, i -> fx.deliver(i % 2 == 0 ? completed : succeeded).status());

        assertThat(statuses).containsOnly(200);
        assertOnePayment(order);
        assertThat(FakePaymentGateway.REFUNDS).isEmpty();
        assertThat(fx.outboxEvents(order.orderId(), "payment.refund_requested")).isZero();
    }

    // ---- out of order -----------------------------------------------------------------------------

    @Test
    void expiredThenCompletedIsALatePaymentThatIsRecordedAndRefundedOnce() throws Exception {
        Placed order = fx.payablePendingOrder();
        String intent = paymentIntent();

        assertThat(fx.deliver(expired(eventId(), order)).status()).isEqualTo(200);
        assertThat(fx.status(order.orderId())).isEqualTo("cancelled");
        assertThat(fx.stock(order.sku())).as("stock given back").isEqualTo(20);
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("released");

        assertThat(fx.deliver(completed(eventId(), order, intent)).status()).isEqualTo(200);
        // Stripe sending the late event again under another id changes nothing.
        assertThat(fx.deliver(completed(eventId(), order, intent)).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).as("a cancelled order never becomes paid").isEqualTo("cancelled");
        assertThat(fx.stock(order.sku())).isEqualTo(20);
        assertThat(fx.payments(order.orderId())).isEqualTo(1);
        assertThat(fx.outboxEvents(order.orderId(), "order.paid")).isZero();
        assertThat(fx.outboxEvents(order.orderId(), "payment.refund_requested")).isEqualTo(1);

        assertThat(processor.drain(50).failed()).isZero();

        assertThat(FakePaymentGateway.REFUNDS)
                .containsExactly(new FakePaymentGateway.Refund(intent, "refund-order-" + order.orderId()));
        assertThat(jdbc.sql("SELECT status || ' ' || refunded_minor || '/' || amount_minor FROM payments WHERE order_id = :id")
                .param("id", order.orderId()).query(String.class).single()).isEqualTo("refunded " + TOTAL + "/" + TOTAL);
        assertThat(fx.count("SELECT count(*) FROM sent_emails")).as("no confirmation for a cancelled order").isZero();
    }

    @Test
    void completedThenExpiredLeavesThePaidOrderPaid() throws Exception {
        Placed order = fx.payablePendingOrder();

        assertThat(fx.deliver(completed(eventId(), order, paymentIntent())).status()).isEqualTo(200);
        assertThat(fx.deliver(expired(eventId(), order)).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("paid");
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("committed");
        assertThat(fx.stock(order.sku())).as("the stock stays sold").isEqualTo(18);
        assertThat(fx.payments(order.orderId())).isEqualTo(1);
        assertThat(fx.outboxEvents(order.orderId(), "order.cancelled")).isZero();
        assertThat(fx.outboxEvents(order.orderId(), "payment.refund_requested")).isZero();
    }

    @Test
    void anExpiryForAnotherSessionDoesNotCancelTheOrder() throws Exception {
        Placed order = fx.payablePendingOrder();
        Placed impostor = new Placed(order.shopper(), order.orderId(), order.sku(), "cs_test_some_other_session");

        assertThat(fx.deliver(expired(eventId(), impostor)).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("pending");
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("active");
        assertThat(fx.stock(order.sku())).isEqualTo(18);
    }

    // ---- delayed payment methods ------------------------------------------------------------------

    @Test
    void aSessionCompletedButUnpaidWaitsForTheAsyncResult() throws Exception {
        Placed order = fx.payablePendingOrder();
        String intent = paymentIntent();

        String unpaid = sessionEvent(eventId(), "checkout.session.completed", order.sessionId(), order.orderId(),
                "unpaid", intent, TOTAL, "usd");
        assertThat(fx.deliver(unpaid).status()).isEqualTo(200);
        assertThat(fx.status(order.orderId())).as("not paid yet").isEqualTo("pending");
        assertThat(fx.payments(order.orderId())).isZero();

        String succeeded = sessionEvent(eventId(), "checkout.session.async_payment_succeeded", order.sessionId(),
                order.orderId(), "paid", intent, TOTAL, "usd");
        assertThat(fx.deliver(succeeded).status()).isEqualTo(200);

        assertOnePayment(order);
    }

    @Test
    void aFailedAsyncPaymentCancelsTheOrderAndReleasesItsStock() throws Exception {
        Placed order = fx.payablePendingOrder();

        String failed = sessionEvent(eventId(), "checkout.session.async_payment_failed", order.sessionId(),
                order.orderId(), "unpaid", null, TOTAL, "usd");
        assertThat(fx.deliver(failed).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("cancelled");
        assertThat(fx.stock(order.sku())).isEqualTo(20);
        assertThat(fx.payments(order.orderId())).isZero();
    }

    // ---- money that cannot be matched to an order we can fulfil -----------------------------------

    @Test
    void anAmountThatDoesNotMatchTheOrderIsRefundedAndTheOrderIsNotFulfilled() throws Exception {
        Placed order = fx.payablePendingOrder();
        String intent = paymentIntent();

        String body = sessionEvent(eventId(), "checkout.session.completed", order.sessionId(), order.orderId(), "paid",
                intent, TOTAL - 100, "usd");
        assertThat(fx.deliver(body).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("cancelled");
        assertThat(fx.stock(order.sku())).as("the hold is given back").isEqualTo(20);
        assertThat(fx.outboxEvents(order.orderId(), "order.paid")).isZero();
        assertThat(jdbc.sql("SELECT amount_minor FROM payments WHERE order_id = :id").param("id", order.orderId())
                .query(Long.class).single()).as("what Stripe actually took").isEqualTo(TOTAL - 100);

        assertThat(processor.drain(50).failed()).isZero();
        assertThat(FakePaymentGateway.REFUNDS).extracting(FakePaymentGateway.Refund::paymentIntentId).containsExactly(intent);
    }

    @Test
    void aCurrencyThatDoesNotMatchTheOrderIsTreatedTheSameWay() throws Exception {
        Placed order = fx.payablePendingOrder();

        String body = sessionEvent(eventId(), "checkout.session.completed", order.sessionId(), order.orderId(), "paid",
                paymentIntent(), TOTAL, "eur");
        assertThat(fx.deliver(body).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("cancelled");
        assertThat(fx.outboxEvents(order.orderId(), "payment.refund_requested")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT currency FROM payments WHERE order_id = :id").param("id", order.orderId())
                .query(String.class).single().trim()).isEqualTo("EUR");
    }

    @Test
    void aPaidOrderWhoseStockHoldIsGoneMovesToRefundedAndNobodyIsToldItIsConfirmed() throws Exception {
        Placed order = fx.payablePendingOrder();
        // The hold disappears behind our back (the situation the refund path exists for).
        jdbc.sql("UPDATE reservations SET status = 'released', resolved_at = now() WHERE order_id = :id")
                .param("id", order.orderId()).update();
        String intent = paymentIntent();

        assertThat(fx.deliver(completed(eventId(), order, intent)).status()).isEqualTo(200);

        assertThat(fx.status(order.orderId())).isEqualTo("refunded");
        assertThat(jdbc.sql("SELECT string_agg(coalesce(from_status, '-') || '>' || to_status, ',' ORDER BY id) "
                        + "FROM order_status_history WHERE order_id = :id")
                .param("id", order.orderId()).query(String.class).single())
                .isEqualTo("->pending,pending>paid,paid>refunded");
        assertThat(fx.payments(order.orderId())).isEqualTo(1);

        assertThat(processor.drain(50).failed()).isZero();

        assertThat(FakePaymentGateway.REFUNDS).extracting(FakePaymentGateway.Refund::paymentIntentId).containsExactly(intent);
        assertThat(fx.count("SELECT count(*) FROM sent_emails")).as("no confirmation e-mail").isZero();
    }

    // ---- events we do not act on ------------------------------------------------------------------

    @Test
    void unknownOrdersAndUnknownEventTypesAreAcknowledgedAndChangeNothing() throws Exception {
        long paymentsBefore = fx.count("SELECT count(*) FROM payments");
        String unknownOrder = eventId();
        String body = sessionEvent(unknownOrder, "checkout.session.completed", "cs_test_x", UUID.randomUUID(), "paid",
                paymentIntent(), TOTAL, "usd");
        assertThat(fx.deliver(body).status()).isEqualTo(200);

        String notAnId = eventId();
        assertThat(fx.deliver(sessionEvent(notAnId, "checkout.session.completed", "cs_test_y", "not-a-uuid", "paid",
                paymentIntent(), TOTAL, "usd")).status()).isEqualTo(200);

        String otherType = eventId();
        assertThat(fx.deliver("{\"id\":\"" + otherType + "\",\"type\":\"payment_intent.created\","
                + "\"data\":{\"object\":{\"id\":\"pi_x\",\"amount\":100}}}").status()).isEqualTo(200);

        assertThat(fx.recordedEvents(unknownOrder)).as("seen, so a retry is a no-op").isEqualTo(1);
        assertThat(fx.recordedEvents(notAnId)).isEqualTo(1);
        assertThat(fx.recordedEvents(otherType)).isEqualTo(1);
        assertThat(fx.count("SELECT count(*) FROM payments")).isEqualTo(paymentsBefore);
        assertThat(fx.count("SELECT count(*) FROM outbox")).isZero();
    }

    @Test
    void aFailureWhileProcessingRollsBackTheEventSoStripesRetrySucceeds() throws Exception {
        Placed order = fx.payablePendingOrder();
        String id = eventId();
        // A paid session with no payment intent cannot be recorded; the handler fails loudly instead of guessing.
        String broken = completed(id, order, null);

        Reply first = fx.deliver(broken);

        assertThat(first.status()).isEqualTo(500);
        assertThat(fx.recordedEvents(id)).as("the event id was rolled back with everything else").isZero();
        assertThat(fx.status(order.orderId())).isEqualTo("pending");
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("active");

        // Stripe retries the same event id; this time the payload is complete.
        assertThat(fx.deliver(completed(id, order, paymentIntent())).status()).isEqualTo(200);
        assertOnePayment(order);
        assertThat(fx.recordedEvents(id)).isEqualTo(1);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** The state "paid exactly once" leaves behind, read independently from the database. */
    private void assertOnePayment(Placed order) {
        assertThat(fx.status(order.orderId())).isEqualTo("paid");
        assertThat(fx.payments(order.orderId())).as("payments rows").isEqualTo(1);
        assertThat(fx.outboxEvents(order.orderId(), "order.paid")).as("order.paid events").isEqualTo(1);
        assertThat(fx.count("SELECT count(*) FROM order_status_history WHERE order_id = ? AND to_status = 'paid'",
                order.orderId())).as("history rows for paid").isEqualTo(1);
        assertThat(fx.reservationStatus(order.orderId())).isEqualTo("committed");
        assertThat(fx.stock(order.sku())).isEqualTo(18);
    }

    private interface Delivery {
        int run(int index) throws Exception;
    }

    /** Runs {@code count} deliveries released together by a latch; returns their statuses. */
    private List<Integer> concurrently(int count, Delivery delivery) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            CountDownLatch ready = new CountDownLatch(count);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return delivery.run(index);
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }
}
