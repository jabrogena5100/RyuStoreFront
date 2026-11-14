package link.ryustorefront.storefront.payments;

/** The payment provider failed or rejected a request. The cause carries the provider's own error. */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
