package link.ryustorefront.storefront.inventory;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Committed test data for inventory tests (the service commits its own transactions, so no rollback). */
class InventoryFixtures {

    private final JdbcClient jdbc;

    InventoryFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    long variant(int stock) {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:slug, 'Test') RETURNING id")
                .param("slug", "c-" + id).query(Long.class).single();
        long product = jdbc.sql("""
                INSERT INTO products (slug, name, brand, category_id, gender)
                VALUES (:slug, 'Test shoe', 'Test brand', :category, 'unisex') RETURNING id""")
                .param("slug", "p-" + id).param("category", category).query(Long.class).single();
        return jdbc.sql("""
                INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock)
                VALUES (:product, :sku, '42', 'black', 12990, :stock) RETURNING id""")
                .param("product", product).param("sku", "SKU-" + id).param("stock", stock)
                .query(Long.class).single();
    }

    UUID order() {
        return jdbc.sql("""
                INSERT INTO orders (contact_email, status, subtotal_minor, shipping_minor, total_minor, shipping_address)
                VALUES ('buyer@example.com', 'pending', 12990, 0, 12990, '{}'::jsonb) RETURNING id""")
                .query(UUID.class).single();
    }

    int stock(long variantId) {
        return jdbc.sql("SELECT stock FROM variants WHERE id = :id").param("id", variantId)
                .query(Integer.class).single();
    }

    long reservations(long variantId, String status) {
        return jdbc.sql("SELECT count(*) FROM reservations WHERE variant_id = :id AND status = :status")
                .param("id", variantId).param("status", status).query(Long.class).single();
    }

    long reservationsForOrder(UUID orderId) {
        return jdbc.sql("SELECT count(*) FROM reservations WHERE order_id = :id")
                .param("id", orderId).query(Long.class).single();
    }

    /** Makes every active reservation look expired without waiting. */
    void expireAllActive() {
        jdbc.sql("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE status = 'active'").update();
    }

    void cleanUp() {
        jdbc.sql("DELETE FROM reservations").update();
        jdbc.sql("DELETE FROM order_items").update();
        jdbc.sql("DELETE FROM orders").update();
        jdbc.sql("DELETE FROM variants").update();
        jdbc.sql("DELETE FROM products").update();
        jdbc.sql("DELETE FROM categories").update();
    }
}
