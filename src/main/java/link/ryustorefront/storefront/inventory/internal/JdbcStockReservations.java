package link.ryustorefront.storefront.inventory.internal;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import link.ryustorefront.storefront.inventory.InsufficientStockException;
import link.ryustorefront.storefront.inventory.Reservation;
import link.ryustorefront.storefront.inventory.StockReservations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plain SQL on purpose (CLAUDE.md section 3): the stock update is the hot, correctness-critical path.
 *
 * Deadlock avoidance: every transaction that touches several variant rows does so in ascending
 * variant id order (reserve sorts its input; releases sort the rows they hold). Reserve only inserts
 * reservation rows and never waits on existing ones, so the variant rows are the only contended locks.
 */
@Service
class JdbcStockReservations implements StockReservations {

    private final JdbcClient jdbc;

    JdbcStockReservations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private record Held(UUID id, long variantId, int quantity) {
    }

    @Override
    @Transactional
    public List<Reservation> reserve(UUID orderId, Map<Long, Integer> quantities, Duration ttl) {
        if (quantities.isEmpty()) {
            throw new IllegalArgumentException("Nothing to reserve");
        }
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        double ttlSeconds = ttl.toMillis() / 1000.0;
        List<Reservation> reservations = new ArrayList<>();

        for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
            long variantId = line.getKey();
            int quantity = line.getValue();
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive for variant " + variantId);
            }

            int taken = jdbc.sql("""
                    UPDATE variants SET stock = stock - :quantity
                    WHERE id = :id AND active AND stock >= :quantity""")
                    .param("quantity", quantity).param("id", variantId).update();
            if (taken == 0) {
                throw new InsufficientStockException(variantId, quantity); // rolls back earlier lines
            }

            reservations.add(jdbc.sql("""
                    INSERT INTO reservations (order_id, variant_id, quantity, expires_at)
                    VALUES (:order, :variant, :quantity, now() + :seconds * interval '1 second')
                    RETURNING id, expires_at""")
                    .param("order", orderId).param("variant", variantId)
                    .param("quantity", quantity).param("seconds", ttlSeconds)
                    .query((rs, i) -> new Reservation(
                            rs.getObject("id", UUID.class), variantId, quantity,
                            rs.getObject("expires_at", OffsetDateTime.class).toInstant()))
                    .single());
        }
        return reservations;
    }

    @Override
    @Transactional
    public int commit(UUID orderId) {
        return jdbc.sql("""
                UPDATE reservations SET status = 'committed', resolved_at = now()
                WHERE order_id = :order AND status = 'active'""")
                .param("order", orderId).update();
    }

    @Override
    @Transactional
    public int release(UUID orderId) {
        // Lock this order's active reservations first so a concurrent commit or sweep waits for us.
        List<Held> held = jdbc.sql("""
                SELECT id, variant_id, quantity FROM reservations
                WHERE order_id = :order AND status = 'active'
                FOR UPDATE""")
                .param("order", orderId).query(JdbcStockReservations::mapHeld).list();
        return releaseAll(held);
    }

    @Override
    @Transactional
    public int releaseExpired(int batchSize) {
        List<Held> expired = jdbc.sql("""
                SELECT id, variant_id, quantity FROM reservations
                WHERE status = 'active' AND expires_at <= now()
                ORDER BY expires_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED""")
                .param("batch", batchSize).query(JdbcStockReservations::mapHeld).list();
        return releaseAll(expired);
    }

    @Override
    @Transactional
    public int extendHold(UUID orderId, Instant newExpiresAt) {
        return jdbc.sql("""
                UPDATE reservations SET expires_at = :t
                WHERE order_id = :order AND status = 'active' AND expires_at < :t""")
                .param("t", OffsetDateTime.ofInstant(newExpiresAt, java.time.ZoneOffset.UTC))
                .param("order", orderId).update();
    }

    /** Releases in ascending variant order (see class comment). The status guard makes a repeat harmless. */
    private int releaseAll(List<Held> held) {
        int released = 0;
        for (Held h : held.stream().sorted(Comparator.comparingLong(Held::variantId)).toList()) {
            int resolved = jdbc.sql("""
                    UPDATE reservations SET status = 'released', resolved_at = now()
                    WHERE id = :id AND status = 'active'""")
                    .param("id", h.id()).update();
            if (resolved == 1) {
                jdbc.sql("UPDATE variants SET stock = stock + :quantity WHERE id = :variant")
                        .param("quantity", h.quantity()).param("variant", h.variantId()).update();
                released++;
            }
        }
        return released;
    }

    private static Held mapHeld(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Held(rs.getObject("id", UUID.class), rs.getLong("variant_id"), rs.getInt("quantity"));
    }
}
