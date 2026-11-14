package link.ryustorefront.storefront.payments;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Stands in for Stripe in every test (it is {@code @Primary}, so it wins over the real gateway, which
 * exists in production only). Records what it was asked, can be told to fail, and is slow enough that a
 * missing lock would show up as more than one session.
 */
@Component
@Primary
public class FakePaymentGateway implements PaymentGateway {

    public static final List<CheckoutRequest> REQUESTS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    public static volatile RuntimeException failWith;

    /** One refund the application asked for. */
    public record Refund(String paymentIntentId, String idempotencyKey) {
    }

    public static final List<Refund> REFUNDS = new CopyOnWriteArrayList<>();
    public static volatile RuntimeException refundFailWith;

    public static void reset() {
        REQUESTS.clear();
        REFUNDS.clear();
        failWith = null;
        refundFailWith = null;
    }

    @Override
    public void refund(String paymentIntentId, String idempotencyKey) {
        RuntimeException failure = refundFailWith;
        if (failure != null) {
            throw failure;
        }
        REFUNDS.add(new Refund(paymentIntentId, idempotencyKey));
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        RuntimeException failure = failWith;
        if (failure != null) {
            throw failure;
        }
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        REQUESTS.add(request);
        String id = "cs_test_fake_" + COUNTER.incrementAndGet();
        return new CheckoutSession(id, "https://checkout.stripe.test/c/pay/" + id);
    }
}
