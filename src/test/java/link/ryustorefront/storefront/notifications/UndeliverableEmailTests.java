package link.ryustorefront.storefront.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A stranger buys on the always-on demo: SES (sandbox) refuses the confirmation for good. The real outbox processor
 * runs both real {@code order.paid} handlers in one transaction; the refusal must not roll back the cart clearing,
 * must leave no "sent" record, and must not be retried.
 */
@SpringBootTest(properties = "spring.mail.host=localhost")
@Import({TestcontainersConfiguration.class, UndeliverableEmailTests.RejectingMail.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UndeliverableEmailTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class RejectingMail {
        static final AtomicInteger attempts = new AtomicInteger();

        @Bean
        @Primary
        EmailSender rejectingSender() {
            return (from, to, content) -> {
                attempts.incrementAndGet();
                throw new UndeliverableEmailException("SES rejected the message: Email address is not verified.", null);
            };
        }
    }

    @Autowired
    Orders orders;
    @Autowired
    OutboxProcessor processor;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        RejectingMail.attempts.set(0);
    }

    @AfterAll
    void cleanUp() {
        jdbc.sql("DELETE FROM outbox").update();
        jdbc.sql("DELETE FROM sent_emails").update();
        String mine = "SELECT id FROM orders WHERE contact_email LIKE 'reject-test-%'";
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_items WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE contact_email LIKE 'reject-test-%'").update();
        jdbc.sql("DELETE FROM customers WHERE email LIKE 'reject-test-%'").update(); // cascades to carts and items
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'REJECTTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'reject-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'reject-test-c-%'").update();
    }

    @Test
    void aPermanentlyRejectedConfirmationStillClearsTheCartAndIsNotRetried() {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:s, 'Test') RETURNING id")
                .param("s", "reject-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("INSERT INTO products (slug, name, brand, category_id, gender) "
                        + "VALUES (:s, 'Reject test', 'Test', :c, 'unisex') RETURNING id")
                .param("s", "reject-test-p-" + id).param("c", category).query(Long.class).single();
        long variant = jdbc.sql("INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock) "
                        + "VALUES (:p, :sku, '42', 'black', 12990, 5) RETURNING id")
                .param("p", product).param("sku", "REJECTTEST-" + id).query(Long.class).single();
        String email = "reject-test-" + id + "@example.com";
        long customer = jdbc.sql("INSERT INTO customers (email, password_hash) VALUES (:e, 'unused') RETURNING id")
                .param("e", email).query(Long.class).single();
        UUID cart = jdbc.sql("INSERT INTO carts (customer_id) VALUES (:c) RETURNING id")
                .param("c", customer).query(UUID.class).single();
        jdbc.sql("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (:cart, :v, 1)")
                .param("cart", cart).param("v", variant).update();
        UUID order = orders.createPending(new NewOrder(customer, email, "USD", 12_990, 800,
                new ShippingAddress("Ada Buyer", "1 Main St", null, "Portland", "OR", "97201", "US"),
                UUID.randomUUID().toString(),
                List.of(new NewOrder.Item(variant, "REJECTTEST-" + id, "Reject test", "42", "black", 12_990, 1))));
        orders.transition(order, OrderStatus.PAID, "test");

        Drained drained = processor.drain(10);

        assertThat(drained).as("the event completed despite the refusal").isEqualTo(new Drained(1, 0));
        assertThat(RejectingMail.attempts.get()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM cart_items WHERE cart_id = :cart").param("cart", cart)
                .query(Long.class).single()).as("the cart was cleared in the same transaction").isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM sent_emails").query(Long.class).single())
                .as("no record claims the mail was sent").isZero();
        assertThat(processor.drain(10)).as("nothing left to retry").isEqualTo(new Drained(0, 0));
        assertThat(RejectingMail.attempts.get()).isEqualTo(1);
    }
}
