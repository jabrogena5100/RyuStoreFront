package link.ryustorefront.storefront.orders;

/**
 * The outcome of asking an order to change status. Returned instead of thrown: a Spring
 * {@code @Transactional} method that throws marks the caller's transaction rollback-only even when
 * the caller catches the exception, which would silently break checkout and webhook handling.
 *
 * @param previous the status the order had when the request was evaluated; null for {@link Outcome#NOT_FOUND}
 */
public record TransitionResult(Outcome outcome, OrderStatus previous) {

    public enum Outcome {
        /** The order moved and a history row was written. */
        APPLIED,
        /** The order was already in the requested status. Nothing changed; this is what a replayed event looks like. */
        ALREADY,
        /** The rules do not allow the move from {@code previous}. Nothing changed. */
        ILLEGAL,
        NOT_FOUND
    }

    public boolean applied() {
        return outcome == Outcome.APPLIED;
    }

    /** True when the order is now in the requested status, whether this call moved it or an earlier one did. */
    public boolean reachedTarget() {
        return outcome == Outcome.APPLIED || outcome == Outcome.ALREADY;
    }
}
