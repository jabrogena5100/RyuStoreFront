package link.ryustorefront.storefront.shared;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns failures of {@link StructuredErrors} controllers into the stable {@code {code, message, fields}} body. */
@RestControllerAdvice(annotations = StructuredErrors.class)
class StructuredErrorHandler {

    private static final List<String> PRIORITY = List.of("required", "length", "min", "max", "invalid_email", "invalid");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(e.body());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            fields.merge(error.getField(), fieldCode(error.getCode()), StructuredErrorHandler::stronger);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("validation_failed", "Check the highlighted fields.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("malformed_request", "The request body is not valid JSON.", Map.of()));
    }

    /** When one field breaks several constraints, report the most basic one first. */
    private static String stronger(String a, String b) {
        return PRIORITY.indexOf(a) <= PRIORITY.indexOf(b) ? a : b;
    }

    /** Field codes are part of the contract; the UI owns the wording. */
    private static String fieldCode(String constraint) {
        return switch (constraint == null ? "" : constraint) {
            case "NotBlank", "NotNull" -> "required";
            case "Size" -> "length";
            case "Min" -> "min";
            case "Max" -> "max";
            case "Email", "Pattern" -> "invalid_email";
            default -> "invalid";
        };
    }
}
