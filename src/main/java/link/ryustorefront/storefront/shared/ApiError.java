package link.ryustorefront.storefront.shared;

import java.util.Map;

/** Error body for the JSON API: a stable code for the UI, per-field codes for forms. See docs/api. */
public record ApiError(String code, String message, Map<String, String> fields) {
}
