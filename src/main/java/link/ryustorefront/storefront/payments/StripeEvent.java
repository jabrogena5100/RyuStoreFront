package link.ryustorefront.storefront.payments;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * The few fields of a Stripe webhook event this application reads, taken from the raw JSON with Gson.
 * Deliberately not the SDK's typed event classes: those are generated for one API version and fail when the
 * account's webhook API version differs, while the fields below are stable. Session fields are filled only
 * for {@code checkout.session.*} events; for every other type they are null.
 *
 * @param orderId the order id we put in the session's metadata (falling back to {@code client_reference_id});
 *                a string, because the payload is untrusted until it is parsed as a UUID
 */
record StripeEvent(String id, String type, String sessionId, String paymentStatus, String paymentIntentId,
                   Long amountTotal, String currency, String orderId) {

    /** @throws IllegalArgumentException if the payload is not a Stripe event object (no id or type) */
    static StripeEvent parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String id = text(root, "id");
            String type = text(root, "type");
            if (id == null || type == null) {
                throw new IllegalArgumentException("event has no id or type");
            }
            if (!type.startsWith("checkout.session.")) {
                return new StripeEvent(id, type, null, null, null, null, null, null);
            }
            JsonObject object = root.getAsJsonObject("data").getAsJsonObject("object");
            String orderId = null;
            if (object.get("metadata") instanceof JsonObject metadata) {
                orderId = text(metadata, "order_id");
            }
            if (orderId == null) {
                orderId = text(object, "client_reference_id");
            }
            JsonElement amount = object.get("amount_total");
            Long amountTotal = amount == null || amount.isJsonNull() ? null : amount.getAsLong();
            return new StripeEvent(id, type, text(object, "id"), text(object, "payment_status"),
                    text(object, "payment_intent"), amountTotal, text(object, "currency"), orderId);
        } catch (JsonParseException | IllegalStateException | ClassCastException | NullPointerException
                 | NumberFormatException e) {
            throw new IllegalArgumentException("not a Stripe event: " + e.getMessage(), e);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() || !element.isJsonPrimitive() ? null : element.getAsString();
    }
}
