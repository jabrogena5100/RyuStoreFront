package link.ryustorefront.storefront.payments;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What Stripe is actually asked for, checked offline from the request map the SDK would send. The
 * mapping matters: amounts must be the order's snapshot in minor units, and the order id must travel
 * with the session because the webhook finds the order from it.
 */
class StripeCheckoutParamsTests {

    private static final UUID ORDER = UUID.fromString("1234abcd-0000-4000-8000-000000000001");
    private static final Instant EXPIRES = Instant.parse("2026-11-14T11:00:00Z");

    private static CheckoutRequest request() {
        return new CheckoutRequest(ORDER, "buyer@example.com", "USD",
                List.of(new CheckoutRequest.Line("Chelsea Boot (size 42, black)", 12_990, 2),
                        new CheckoutRequest.Line("Shipping", 800, 1)),
                EXPIRES, "https://shop.test/ok?session_id={CHECKOUT_SESSION_ID}", "https://shop.test/cart");
    }

    @SuppressWarnings("unchecked")
    @Test
    void theRequestCarriesAmountsLinesOrderIdAndExpiry() {
        SessionCreateParams params = StripeCheckoutParams.from(request());

        Map<String, Object> map = params.toMap();

        assertThat(map.get("mode")).isEqualTo("payment");
        assertThat(map.get("client_reference_id")).isEqualTo(ORDER.toString());
        assertThat(map.get("customer_email")).isEqualTo("buyer@example.com");
        assertThat(map.get("success_url")).isEqualTo("https://shop.test/ok?session_id={CHECKOUT_SESSION_ID}");
        assertThat(map.get("cancel_url")).isEqualTo("https://shop.test/cart");
        assertThat(((Number) map.get("expires_at")).longValue()).isEqualTo(EXPIRES.getEpochSecond());
        assertThat((Map<String, Object>) map.get("metadata")).containsEntry("order_id", ORDER.toString());

        List<Map<String, Object>> items = (List<Map<String, Object>>) map.get("line_items");
        assertThat(items).hasSize(2);
        Map<String, Object> boots = items.get(0);
        assertThat(((Number) boots.get("quantity")).longValue()).isEqualTo(2);
        Map<String, Object> bootPrice = (Map<String, Object>) boots.get("price_data");
        assertThat(bootPrice.get("currency")).as("Stripe wants lowercase").isEqualTo("usd");
        assertThat(((Number) bootPrice.get("unit_amount")).longValue()).as("minor units, not a decimal").isEqualTo(12_990);
        assertThat(((Map<String, Object>) bootPrice.get("product_data")).get("name"))
                .isEqualTo("Chelsea Boot (size 42, black)");
        Map<String, Object> shipping = (Map<String, Object>) items.get(1).get("price_data");
        assertThat(((Number) shipping.get("unit_amount")).longValue()).isEqualTo(800);
    }
}
