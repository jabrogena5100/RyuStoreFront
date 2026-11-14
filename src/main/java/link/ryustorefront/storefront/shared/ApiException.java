package link.ryustorefront.storefront.shared;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Thrown by a controller annotated {@link StructuredErrors}; rendered as an {@link ApiError}. */
public final class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.code = code;
        this.fields = fields;
    }

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public HttpStatus status() {
        return status;
    }

    public ApiError body() {
        return new ApiError(code, getMessage(), fields);
    }
}
