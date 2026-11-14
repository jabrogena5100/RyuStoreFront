package link.ryustorefront.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the real Flyway migrations on a real Postgres (Testcontainers) and checks that each
 * constraint rejects bad data. Every test rolls back. Each rejection assertion names the
 * constraint that must fire, so a test cannot pass because some unrelated column failed.
 *
 * One rejected statement per test: after an error Postgres aborts the surrounding transaction.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SchemaConstraintTests {

    @Autowired
    JdbcClient jdbc;

    // ---- migrations -------------------------------------------------------------------------

    @Test
    void allMigrationsAppliedSuccessfully() {
        long failed = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success = false")
                .query(Long.class).single();
        long applied = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL")
                .query(Long.class).single();

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(4);
    }

    // ---- catalog ----------------------------------------------------------------------------

    @Test
    void stockCannotBeSetNegative() {
        long variant = variant(3);
        rejected("variants_stock_nonneg",
                () -> jdbc.sql("UPDATE variants SET stock = -1 WHERE id = ?").param(variant).update());
    }

    @Test
    void decrementingStockBelowZeroIsRejected() {
        long variant = variant(3);
        rejected("variants_stock_nonneg",
                () -> jdbc.sql("UPDATE variants SET stock = stock - 4 WHERE id = ?").param(variant).update());
    }

    @Test
    void priceCannotBeNegative() {
        long product = product(category());
        rejected("variants_price_nonneg", () -> insertVariant(product, "SKU-NEG", "42", "black", -1, 1));
    }

    @Test
    void duplicateSkuIsRejected() {
        long product = product(category());
        insertVariant(product, "SKU-DUP", "42", "black", 1000, 1);
        rejected("variants_sku_uniq", () -> insertVariant(product, "SKU-DUP", "43", "black", 1000, 1));
    }

    @Test
    void sameSizeAndColourTwiceForOneProductIsRejected() {
        long product = product(category());
        insertVariant(product, "SKU-A", "42", "black", 1000, 1);
        rejected("variants_product_size_colour_uniq", () -> insertVariant(product, "SKU-B", "42", "black", 1000, 1));
    }

    @Test
    void productNeedsAnExistingCategory() {
        rejected("products_category_fk", () -> insertProduct("orphan", 999_999_999L, "unisex"));
    }

    @Test
    void productGenderMustBeKnown() {
        long category = category();
        rejected("products_gender_valid", () -> insertProduct("odd-gender", category, "kids"));
    }

    @Test
    void productSlugMustBeUrlSafe() {
        long category = category();
        rejected("products_slug_format", () -> insertProduct("Not A Slug", category, "men"));
    }

    // ---- carts ------------------------------------------------------------------------------

    @Test
    void cartWithNoOwnerIsRejected() {
        rejected("carts_one_owner", () -> jdbc.sql("INSERT INTO carts DEFAULT VALUES").update());
    }

    @Test
    void cartWithTwoOwnersIsRejected() {
        long customer = customer("two-owners@example.com");
        rejected("carts_one_owner", () -> jdbc.sql("INSERT INTO carts (customer_id, guest_token_hash) VALUES (?, 'abc')")
                .param(customer).update());
    }

    @Test
    void customerCanHaveOnlyOneCart() {
        long customer = customer("one-cart@example.com");
        jdbc.sql("INSERT INTO carts (customer_id) VALUES (?)").param(customer).update();
        rejected("carts_customer_uniq",
                () -> jdbc.sql("INSERT INTO carts (customer_id) VALUES (?)").param(customer).update());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    void cartItemQuantityMustBeBetweenOneAndTen(int quantity) {
        UUID cart = guestCart("g-" + quantity);
        long variant = variant(5);
        rejected("cart_items_quantity_range", () -> jdbc
                .sql("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (?, ?, ?)")
                .params(cart, variant, quantity).update());
    }

    @Test
    void sameVariantTwiceInOneCartIsRejected() {
        UUID cart = guestCart("dup-item");
        long variant = variant(5);
        jdbc.sql("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (?, ?, 1)").params(cart, variant).update();
        rejected("cart_items_cart_variant_uniq", () -> jdbc
                .sql("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (?, ?, 1)")
                .params(cart, variant).update());
    }

    @Test
    void customerEmailMustBeLowercase() {
        rejected("customers_email_lowercase", () -> customer("Mixed.Case@Example.com"));
    }

    // ---- orders -----------------------------------------------------------------------------

    @Test
    void orderTotalMustEqualSubtotalPlusShipping() {
        rejected("orders_total_matches", () -> order("pending", 10_000, 500, 10_000));
    }

    @Test
    void orderStatusMustBeKnown() {
        rejected("orders_status_valid", () -> order("teleported", 10_000, 500, 10_500));
    }

    @Test
    void everyLifecycleStatusIsAccepted() {
        for (String status : new String[] {"pending", "paid", "fulfilled", "shipped", "cancelled", "refunded"}) {
            assertThatCode(() -> order(status, 10_000, 500, 10_500)).doesNotThrowAnyException();
        }
    }

    @Test
    void orderItemQuantityMustBePositive() {
        UUID order = order("pending", 10_000, 500, 10_500);
        long variant = variant(5);
        rejected("order_items_quantity_positive", () -> insertOrderItem(order, variant, 0));
    }

    @Test
    void aVariantThatWasOrderedCannotBeDeleted() {
        UUID order = order("pending", 10_000, 500, 10_500);
        long variant = variant(5);
        insertOrderItem(order, variant, 1);
        rejected("order_items_variant_fk",
                () -> jdbc.sql("DELETE FROM variants WHERE id = ?").param(variant).update());
    }

    // ---- payments and webhook idempotency ---------------------------------------------------

    @Test
    void anOrderCanHaveOnlyOnePayment() {
        UUID order = order("paid", 10_000, 500, 10_500);
        insertPayment(order, "pi_first", 10_500, 0);
        rejected("payments_order_uniq", () -> insertPayment(order, "pi_second", 10_500, 0));
    }

    @Test
    void aPaymentIntentCanBeRecordedOnlyOnce() {
        UUID first = order("paid", 10_000, 500, 10_500);
        UUID second = order("paid", 10_000, 500, 10_500);
        insertPayment(first, "pi_same", 10_500, 0);
        rejected("payments_intent_uniq", () -> insertPayment(second, "pi_same", 10_500, 0));
    }

    @Test
    void refundsCannotExceedThePayment() {
        UUID order = order("paid", 10_000, 500, 10_500);
        rejected("payments_refund_within_amount", () -> insertPayment(order, "pi_over", 10_500, 10_501));
    }

    @Test
    void replayedStripeEventIsDetectedByTheInsert() {
        int firstDelivery = insertStripeEvent("evt_123");
        int secondDelivery = insertStripeEvent("evt_123");
        int thirdDelivery = insertStripeEvent("evt_123");

        assertThat(firstDelivery).isEqualTo(1);
        assertThat(secondDelivery).isZero();
        assertThat(thirdDelivery).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM stripe_events WHERE event_id = 'evt_123'")
                .query(Long.class).single()).isEqualTo(1L);
    }

    // ---- reservations and outbox ------------------------------------------------------------

    @Test
    void reservationQuantityMustBePositive() {
        UUID order = order("pending", 10_000, 500, 10_500);
        long variant = variant(5);
        rejected("reservations_quantity_positive", () -> insertReservation(order, variant, 0, "active", false));
    }

    @Test
    void anActiveReservationCannotHaveAResolvedTime() {
        UUID order = order("pending", 10_000, 500, 10_500);
        long variant = variant(5);
        rejected("reservations_resolved_matches_status", () -> insertReservation(order, variant, 1, "active", true));
    }

    @Test
    void aReleasedReservationMustHaveAResolvedTime() {
        UUID order = order("pending", 10_000, 500, 10_500);
        long variant = variant(5);
        rejected("reservations_resolved_matches_status", () -> insertReservation(order, variant, 1, "released", false));
    }

    @Test
    void outboxAttemptsCannotBeNegative() {
        rejected("outbox_attempts_nonneg", () -> jdbc.sql("""
                INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload, attempts)
                VALUES ('order', '1', 'order.paid', '{}'::jsonb, -1)""").update());
    }

    // ---- helpers ----------------------------------------------------------------------------

    private void rejected(String constraint, org.assertj.core.api.ThrowableAssert.ThrowingCallable statement) {
        assertThatThrownBy(statement)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private long category() {
        return jdbc.sql("INSERT INTO categories (slug, name) VALUES ('boots', 'Boots') RETURNING id")
                .query(Long.class).single();
    }

    private long insertProduct(String slug, long categoryId, String gender) {
        return jdbc.sql("""
                INSERT INTO products (slug, name, brand, category_id, gender)
                VALUES (?, 'Test shoe', 'Test brand', ?, ?) RETURNING id""")
                .params(slug, categoryId, gender).query(Long.class).single();
    }

    private long product(long categoryId) {
        return insertProduct("test-shoe", categoryId, "unisex");
    }

    private long insertVariant(long productId, String sku, String size, String colour, long priceMinor, int stock) {
        return jdbc.sql("""
                INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id""")
                .params(productId, sku, size, colour, priceMinor, stock).query(Long.class).single();
    }

    private long variant(int stock) {
        return insertVariant(product(category()), "SKU-" + UUID.randomUUID(), "42", "black", 12_990, stock);
    }

    private long customer(String email) {
        return jdbc.sql("INSERT INTO customers (email, password_hash) VALUES (?, 'x') RETURNING id")
                .param(email).query(Long.class).single();
    }

    private UUID guestCart(String tokenHash) {
        return jdbc.sql("INSERT INTO carts (guest_token_hash) VALUES (?) RETURNING id")
                .param(tokenHash).query(UUID.class).single();
    }

    private UUID order(String status, long subtotal, long shipping, long total) {
        return jdbc.sql("""
                INSERT INTO orders (contact_email, status, subtotal_minor, shipping_minor, total_minor, shipping_address)
                VALUES ('buyer@example.com', ?, ?, ?, ?, '{}'::jsonb) RETURNING id""")
                .params(status, subtotal, shipping, total).query(UUID.class).single();
    }

    private void insertOrderItem(UUID order, long variant, int quantity) {
        jdbc.sql("""
                INSERT INTO order_items (order_id, variant_id, sku, product_name, size_label, colour, unit_price_minor, quantity)
                VALUES (?, ?, 'SKU', 'Test shoe', '42', 'black', 12990, ?)""")
                .params(order, variant, quantity).update();
    }

    private void insertPayment(UUID order, String intentId, long amount, long refunded) {
        jdbc.sql("""
                INSERT INTO payments (order_id, stripe_payment_intent_id, amount_minor, refunded_minor)
                VALUES (?, ?, ?, ?)""").params(order, intentId, amount, refunded).update();
    }

    private int insertStripeEvent(String eventId) {
        return jdbc.sql("INSERT INTO stripe_events (event_id, event_type) VALUES (?, 'checkout.session.completed') ON CONFLICT DO NOTHING")
                .param(eventId).update();
    }

    private void insertReservation(UUID order, long variant, int quantity, String status, boolean resolved) {
        jdbc.sql("""
                INSERT INTO reservations (order_id, variant_id, quantity, status, expires_at, resolved_at)
                VALUES (?, ?, ?, ?, now() + interval '15 minutes', CASE WHEN ? THEN now() END)""")
                .params(order, variant, quantity, status, resolved).update();
    }
}
