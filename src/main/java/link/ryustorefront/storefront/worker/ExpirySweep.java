package link.ryustorefront.storefront.worker;

import java.util.List;
import java.util.UUID;
import link.ryustorefront.storefront.inventory.StockReservations;
import link.ryustorefront.storefront.orders.Orders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives up on abandoned checkouts: cancels pending orders whose hold has run out and returns their
 * stock. Safe to run from several workers at once and to repeat: each order is cancelled, and its
 * stock released, by exactly one caller.
 *
 * <p>Lock order matches the rest of the application (see {@code Orders#transition}): the order row
 * first, then its reservations. Each order is handled in its own transaction so one bad row cannot
 * block the others.
 */
@Service
public class ExpirySweep {

    /** What one sweep did. */
    public record Result(int ordersCancelled, int strayReservationsReleased) {
    }

    private final Orders orders;
    private final StockReservations stock;
    private final TransactionTemplate transaction;

    ExpirySweep(Orders orders, StockReservations stock, PlatformTransactionManager transactionManager) {
        this.orders = orders;
        this.stock = stock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Result sweepOnce(int batchSize) {
        List<UUID> overdue = orders.overduePendingIds(batchSize);
        int cancelled = 0;
        for (UUID orderId : overdue) {
            Boolean done = transaction.execute(status -> {
                if (orders.cancelPending(orderId, "payment window expired")) {
                    stock.release(orderId);
                    return true;
                }
                return false; // paid, cancelled or swept by someone else in the meantime
            });
            if (Boolean.TRUE.equals(done)) {
                cancelled++;
            }
        }
        // Only after the orders: a reservation must not be released while its order is still pending
        // and could be paid. Anything left here belongs to no pending order.
        int stray = stock.releaseExpired(batchSize);
        return new Result(cancelled, stray);
    }
}
