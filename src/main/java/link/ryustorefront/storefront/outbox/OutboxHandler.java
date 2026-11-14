package link.ryustorefront.storefront.outbox;

/**
 * Reacts to one event type. Declare an implementation as a Spring bean and the worker finds it.
 *
 * <p>Delivery is <b>at least once</b>: the event is marked processed in the same transaction as the
 * handler's database work, so a crash or kill before commit means the event is delivered again. A
 * handler must therefore be idempotent (safe to run twice for the same event). Database work done by
 * the handler is rolled back if it throws; anything outside the database (an email sent, an HTTP call)
 * is not, so guard such calls with a record of having done them.
 *
 * <p>Throwing means "try again later" (with backoff, up to the attempt limit).
 */
public interface OutboxHandler {

    String eventType();

    void handle(OutboxEvent event);
}
