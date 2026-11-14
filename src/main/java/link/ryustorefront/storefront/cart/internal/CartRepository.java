package link.ryustorefront.storefront.cart.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL over carts and cart_items. The schema (V2) enforces one cart per owner and quantity 1 to 10. */
@Repository
public class CartRepository {

    /** A sellable variant as the cart sees it: {@code active} means the variant and its product are both active. */
    public record Variant(long id, boolean active, int stock) {
    }

    public record Line(long variantId, String sku, String productSlug, String productName, String brand, String sizeLabel,
                       String colour, long unitPriceMinor, String currency, int quantity,
                       boolean active, int stock) {
    }

    private static final RowMapper<Line> LINE = (rs, n) -> new Line(
            rs.getLong("variant_id"), rs.getString("sku"), rs.getString("slug"), rs.getString("name"), rs.getString("brand"),
            rs.getString("size_label"), rs.getString("colour"), rs.getLong("price_minor"),
            rs.getString("currency").trim(), rs.getInt("quantity"), rs.getBoolean("active"), rs.getInt("stock"));

    private final JdbcClient jdbc;

    public CartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findByCustomer(long customerId) {
        return jdbc.sql("SELECT id FROM carts WHERE customer_id = :c").param("c", customerId)
                .query(UUID.class).optional();
    }

    public Optional<UUID> findByGuestHash(String hash) {
        return jdbc.sql("SELECT id FROM carts WHERE guest_token_hash = :h").param("h", hash)
                .query(UUID.class).optional();
    }

    /** Race-safe: two concurrent first writes by one customer end up on the same cart. */
    public UUID getOrCreateForCustomer(long customerId) {
        return jdbc.sql("INSERT INTO carts (customer_id) VALUES (:c) "
                        + "ON CONFLICT ON CONSTRAINT carts_customer_uniq DO UPDATE SET updated_at = now() RETURNING id")
                .param("c", customerId).query(UUID.class).single();
    }

    public UUID createForGuest(String hash) {
        return jdbc.sql("INSERT INTO carts (guest_token_hash) VALUES (:h) RETURNING id")
                .param("h", hash).query(UUID.class).single();
    }

    public Optional<Variant> findVariant(String sku) {
        return jdbc.sql("SELECT v.id, (v.active AND p.active) AS active, v.stock "
                        + "FROM variants v JOIN products p ON p.id = v.product_id WHERE v.sku = :sku")
                .param("sku", sku)
                .query((rs, n) -> new Variant(rs.getLong("id"), rs.getBoolean("active"), rs.getInt("stock")))
                .optional();
    }

    /** Sets (not increments) the quantity, so repeating the call changes nothing. */
    public void setQuantity(UUID cartId, long variantId, int quantity) {
        jdbc.sql("INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES (:cart, :variant, :q) "
                        + "ON CONFLICT (cart_id, variant_id) DO UPDATE SET quantity = EXCLUDED.quantity")
                .param("cart", cartId).param("variant", variantId).param("q", quantity).update();
        touch(cartId);
    }

    public void removeItem(UUID cartId, long variantId) {
        jdbc.sql("DELETE FROM cart_items WHERE cart_id = :cart AND variant_id = :variant")
                .param("cart", cartId).param("variant", variantId).update();
        touch(cartId);
    }

    public List<Line> lines(UUID cartId) {
        return jdbc.sql("SELECT v.id AS variant_id, v.sku, p.slug, p.name, p.brand, v.size_label, v.colour, v.price_minor, v.currency, "
                        + "ci.quantity, (v.active AND p.active) AS active, v.stock "
                        + "FROM cart_items ci JOIN variants v ON v.id = ci.variant_id "
                        + "JOIN products p ON p.id = v.product_id "
                        + "WHERE ci.cart_id = :cart ORDER BY ci.added_at, ci.id")
                .param("cart", cartId).query(LINE).list();
    }

    /** Locks the cart row for the rest of the transaction; false if it no longer exists. */
    public boolean lock(UUID cartId) {
        return jdbc.sql("SELECT id FROM carts WHERE id = :id FOR UPDATE").param("id", cartId)
                .query(UUID.class).optional().isPresent();
    }

    /** Copies every line of {@code from} into {@code into}; on a shared variant the larger quantity wins. */
    public void mergeInto(UUID from, UUID into) {
        jdbc.sql("INSERT INTO cart_items (cart_id, variant_id, quantity) "
                        + "SELECT :into, variant_id, quantity FROM cart_items WHERE cart_id = :from "
                        + "ON CONFLICT (cart_id, variant_id) "
                        + "DO UPDATE SET quantity = GREATEST(cart_items.quantity, EXCLUDED.quantity)")
                .param("into", into).param("from", from).update();
        touch(into);
    }

    public void delete(UUID cartId) {
        jdbc.sql("DELETE FROM carts WHERE id = :id").param("id", cartId).update();
    }

    private void touch(UUID cartId) {
        jdbc.sql("UPDATE carts SET updated_at = now() WHERE id = :id").param("id", cartId).update();
    }
}
