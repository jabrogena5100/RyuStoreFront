package link.ryustorefront.storefront.checkout;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Committed test rows, all prefixed so {@link #cleanUp()} removes exactly them, in foreign-key order.
 * Cleanup matters: other test classes share this database and delete every variant, and orders,
 * order_items and reservations reference variants with ON DELETE RESTRICT.
 */
final class CheckoutFixtures {

    record Item(long id, String sku) {
    }

    static final String EMAIL_PREFIX = "chk-test-";

    private final JdbcClient jdbc;

    CheckoutFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Item variant(int stock, long priceMinor) {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:slug, 'Test') RETURNING id")
                .param("slug", "chk-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("""
                INSERT INTO products (slug, name, brand, category_id, gender)
                VALUES (:slug, 'Checkout test boot', 'Test brand', :category, 'unisex') RETURNING id""")
                .param("slug", "chk-test-p-" + id).param("category", category).query(Long.class).single();
        String sku = "CHKTEST-" + id;
        long variant = jdbc.sql("""
                INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock)
                VALUES (:product, :sku, '42', 'black', :price, :stock) RETURNING id""")
                .param("product", product).param("sku", sku).param("price", priceMinor).param("stock", stock)
                .query(Long.class).single();
        return new Item(variant, sku);
    }

    /** A customer row created directly (no password worth logging in with), for tests that call the service. */
    long customer() {
        return jdbc.sql("INSERT INTO customers (email, password_hash) VALUES (:e, 'unused') RETURNING id")
                .param("e", uniqueEmail()).query(Long.class).single();
    }

    String emailOf(long customerId) {
        return jdbc.sql("SELECT email FROM customers WHERE id = :id").param("id", customerId).query(String.class).single();
    }

    long customerIdByEmail(String email) {
        return jdbc.sql("SELECT id FROM customers WHERE email = :e").param("e", email).query(Long.class).single();
    }

    static String uniqueEmail() {
        return EMAIL_PREFIX + UUID.randomUUID() + "@example.com";
    }

    /** Replaces the customer's cart contents with a single line. */
    void cartOf(long customerId, Item item, int quantity) {
        UUID cart = jdbc.sql("INSERT INTO carts (customer_id) VALUES (:c) "
                        + "ON CONFLICT ON CONSTRAINT carts_customer_uniq DO UPDATE SET updated_at = now() RETURNING id")
                .param("c", customerId).query(UUID.class).single();
        jdbc.sql("DELETE FROM cart_items WHERE cart_id = :c").param("c", cart).update();
        addToCart(customerId, item, quantity);
    }

    void addToCart(long customerId, Item item, int quantity) {
        jdbc.sql("INSERT INTO cart_items (cart_id, variant_id, quantity) "
                        + "VALUES ((SELECT id FROM carts WHERE customer_id = :c), :v, :q)")
                .param("c", customerId).param("v", item.id()).param("q", quantity).update();
    }

    int stock(Item item) {
        return jdbc.sql("SELECT stock FROM variants WHERE id = :id").param("id", item.id()).query(Integer.class).single();
    }

    void setStock(Item item, int stock) {
        jdbc.sql("UPDATE variants SET stock = :s WHERE id = :id").param("s", stock).param("id", item.id()).update();
    }

    void setPrice(Item item, long priceMinor) {
        jdbc.sql("UPDATE variants SET price_minor = :p WHERE id = :id").param("p", priceMinor).param("id", item.id()).update();
    }

    void setVariantActive(Item item, boolean active) {
        jdbc.sql("UPDATE variants SET active = :a WHERE id = :id").param("a", active).param("id", item.id()).update();
    }

    void cleanUp() {
        String mine = "SELECT o.id FROM orders o JOIN customers c ON c.id = o.customer_id WHERE c.email LIKE 'chk-test-%'";
        jdbc.sql("DELETE FROM reservations WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_status_history WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM order_items WHERE order_id IN (" + mine + ")").update();
        jdbc.sql("DELETE FROM orders WHERE customer_id IN (SELECT id FROM customers WHERE email LIKE 'chk-test-%')").update();
        jdbc.sql("DELETE FROM cart_items WHERE variant_id IN (SELECT id FROM variants WHERE sku LIKE 'CHKTEST-%')").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'CHKTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'chk-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'chk-test-c-%'").update();
        jdbc.sql("DELETE FROM customers WHERE email LIKE 'chk-test-%'").update();
    }
}
