package link.ryustorefront.storefront.inventory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds stock for an order until it is paid (committed) or abandoned (released or expired).
 *
 * Stock is taken out of {@code variants.stock} when a reservation is made, with a single atomic
 * conditional UPDATE, so concurrent buyers can never oversell: the database serialises them on the
 * variant row and the CHECK (stock >= 0) constraint is only a backstop. Every method is
 * {@code @Transactional} (REQUIRED): it joins the caller's transaction when there is one (checkout
 * reserves stock in the same transaction that creates the order) and runs in its own otherwise. The
 * order must already exist (reservations reference it).
 */
public interface StockReservations {

    /**
     * Reserves {@code quantities} (variant id to units) for the order, all or nothing.
     *
     * @throws InsufficientStockException if any variant lacks stock or is inactive; nothing is reserved
     */
    List<Reservation> reserve(UUID orderId, Map<Long, Integer> quantities, Duration ttl);

    /**
     * Marks the order's active reservations as committed (the stock stays taken). Idempotent.
     * Succeeds for a reservation whose time has passed but which the sweep has not released yet.
     *
     * @return how many reservations were newly committed (0 if already committed or already released)
     */
    int commit(UUID orderId);

    /**
     * Gives the order's active reservations back to stock. Idempotent: a second call restores nothing.
     *
     * @return how many reservations were released
     */
    int release(UUID orderId);

    /**
     * Releases up to {@code batchSize} reservations whose time has passed. Safe to run from several
     * workers at once: each reservation is released by exactly one of them.
     *
     * @return how many reservations were released
     */
    int releaseExpired(int batchSize);
}
