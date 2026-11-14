package link.ryustorefront.storefront.cart;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Committed test catalogue rows, all prefixed so {@link #cleanUp()} removes exactly them. Cleanup
 * matters: cart_items references variants (ON DELETE RESTRICT), and other test classes share this
 * database and delete every variant when they start or finish.
 */
final class CartFixtures {

    record Item(long id, String sku) {
    }

    private final JdbcClient jdbc;

    CartFixtures(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Item variant(int stock, long priceMinor) {
        String id = UUID.randomUUID().toString();
        long category = jdbc.sql("INSERT INTO categories (slug, name) VALUES (:slug, 'Test') RETURNING id")
                .param("slug", "cart-test-c-" + id).query(Long.class).single();
        long product = jdbc.sql("""
                INSERT INTO products (slug, name, brand, category_id, gender)
                VALUES (:slug, 'Cart test boot', 'Test brand', :category, 'unisex') RETURNING id""")
                .param("slug", "cart-test-p-" + id).param("category", category).query(Long.class).single();
        String sku = "CARTTEST-" + id;
        long variant = jdbc.sql("""
                INSERT INTO variants (product_id, sku, size_label, colour, price_minor, stock)
                VALUES (:product, :sku, '42', 'black', :price, :stock) RETURNING id""")
                .param("product", product).param("sku", sku).param("price", priceMinor).param("stock", stock)
                .query(Long.class).single();
        return new Item(variant, sku);
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

    void setProductActive(Item item, boolean active) {
        jdbc.sql("UPDATE products SET active = :a WHERE id = (SELECT product_id FROM variants WHERE id = :id)")
                .param("a", active).param("id", item.id()).update();
    }

    void cleanUp() {
        jdbc.sql("DELETE FROM cart_items WHERE variant_id IN (SELECT id FROM variants WHERE sku LIKE 'CARTTEST-%')").update();
        jdbc.sql("DELETE FROM variants WHERE sku LIKE 'CARTTEST-%'").update();
        jdbc.sql("DELETE FROM products WHERE slug LIKE 'cart-test-p-%'").update();
        jdbc.sql("DELETE FROM categories WHERE slug LIKE 'cart-test-c-%'").update();
    }
}
