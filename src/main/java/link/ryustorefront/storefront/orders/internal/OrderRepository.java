package link.ryustorefront.storefront.orders.internal;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import link.ryustorefront.storefront.orders.NewOrder;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.ShippingAddress;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL over orders, order_items and order_status_history. */
@Repository
public class OrderRepository {

    private final JdbcClient jdbc;

    public OrderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Serialises checkouts of one customer until the surrounding transaction ends. */
    public void lockCustomer(long customerId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:id)").param("id", customerId).query((rs, n) -> 0).list();
    }

    public UUID insertOrder(NewOrder order) {
        ShippingAddress a = order.address();
        long total = Math.addExact(order.subtotalMinor(), order.shippingMinor());
        // The address is built by Postgres from bound parameters, so no JSON is assembled by hand.
        return jdbc.sql("""
                INSERT INTO orders (customer_id, contact_email, currency, subtotal_minor, shipping_minor,
                                    total_minor, shipping_address, idempotency_key)
                VALUES (:customer, :email, :currency, :subtotal, :shipping, :total,
                        jsonb_build_object('name', CAST(:name AS text), 'line1', CAST(:line1 AS text),
                                           'line2', CAST(:line2 AS text), 'city', CAST(:city AS text),
                                           'region', CAST(:region AS text), 'postalCode', CAST(:postal AS text),
                                           'country', CAST(:country AS text)),
                        :key)
                RETURNING id""")
                .param("customer", order.customerId())
                .param("email", order.contactEmail())
                .param("currency", order.currency())
                .param("subtotal", order.subtotalMinor())
                .param("shipping", order.shippingMinor())
                .param("total", total)
                .param("name", a.name())
                .param("line1", a.line1())
                .param("line2", a.line2(), Types.VARCHAR)
                .param("city", a.city())
                .param("region", a.region())
                .param("postal", a.postalCode())
                .param("country", a.country())
                .param("key", order.idempotencyKey())
                .query(UUID.class).single();
    }

    public void insertItem(UUID orderId, NewOrder.Item item) {
        jdbc.sql("""
                INSERT INTO order_items (order_id, variant_id, sku, product_name, size_label, colour,
                                         unit_price_minor, quantity)
                VALUES (:order, :variant, :sku, :name, :size, :colour, :price, :qty)""")
                .param("order", orderId).param("variant", item.variantId()).param("sku", item.sku())
                .param("name", item.productName()).param("size", item.sizeLabel()).param("colour", item.colour())
                .param("price", item.unitPriceMinor()).param("qty", item.quantity())
                .update();
    }

    public void insertHistory(UUID orderId, String from, String to, String reason) {
        jdbc.sql("INSERT INTO order_status_history (order_id, from_status, to_status, reason) "
                        + "VALUES (:order, :from, :to, :reason)")
                .param("order", orderId).param("from", from, Types.VARCHAR).param("to", to).param("reason", reason)
                .update();
    }

    public void setExpiry(UUID orderId, Instant expiresAt) {
        jdbc.sql("UPDATE orders SET expires_at = :t, updated_at = now() WHERE id = :id")
                .param("t", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)).param("id", orderId).update();
    }

    /** Reads the order's status and locks its row until the transaction ends; empty if there is no such order. */
    public Optional<String> lockStatus(UUID orderId) {
        return jdbc.sql("SELECT status FROM orders WHERE id = :id FOR UPDATE").param("id", orderId)
                .query(String.class).optional();
    }

    /** Writes the new status. Leaving pending also clears the hold expiry. Callers hold the row lock. */
    public void updateStatus(UUID orderId, String status) {
        jdbc.sql("UPDATE orders SET status = :s, expires_at = NULL, version = version + 1, updated_at = now() "
                        + "WHERE id = :id")
                .param("s", status).param("id", orderId).update();
    }

    public List<UUID> pendingOrderIds(long customerId) {
        return jdbc.sql("SELECT id FROM orders WHERE customer_id = :c AND status = 'pending' ORDER BY created_at")
                .param("c", customerId).query(UUID.class).list();
    }

    public Optional<UUID> findIdByKey(long customerId, String key) {
        return jdbc.sql("SELECT id FROM orders WHERE customer_id = :c AND idempotency_key = :k")
                .param("c", customerId).param("k", key).query(UUID.class).optional();
    }

    public Optional<OrderSummary> find(UUID orderId) {
        List<OrderSummary.Line> items = jdbc.sql("""
                SELECT sku, product_name, size_label, colour, unit_price_minor, quantity
                FROM order_items WHERE order_id = :id ORDER BY id""")
                .param("id", orderId)
                .query((rs, n) -> new OrderSummary.Line(rs.getString("sku"), rs.getString("product_name"),
                        rs.getString("size_label"), rs.getString("colour"), rs.getLong("unit_price_minor"),
                        rs.getInt("quantity"),
                        Math.multiplyExact(rs.getLong("unit_price_minor"), (long) rs.getInt("quantity"))))
                .list();
        return jdbc.sql("""
                SELECT id, status, contact_email, currency, subtotal_minor, shipping_minor, total_minor,
                       expires_at, created_at
                FROM orders WHERE id = :id""")
                .param("id", orderId)
                .query((rs, n) -> {
                    OffsetDateTime expires = rs.getObject("expires_at", OffsetDateTime.class);
                    return new OrderSummary(rs.getObject("id", UUID.class), rs.getString("status"),
                            rs.getString("contact_email"), rs.getString("currency").trim(),
                            rs.getLong("subtotal_minor"), rs.getLong("shipping_minor"), rs.getLong("total_minor"),
                            expires == null ? null : expires.toInstant(),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant(), items);
                })
                .optional();
    }
}
