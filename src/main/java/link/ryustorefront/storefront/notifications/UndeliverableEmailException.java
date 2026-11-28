package link.ryustorefront.storefront.notifications;

/**
 * The mail service refused this message for good (for example SES {@code MessageRejected}: in the sandbox, a recipient
 * that is not verified). Retrying cannot help, so {@link OrderPaidEmailHandler} logs it and lets the event complete
 * instead of rolling back the other {@code order.paid} work (cart clearing) with it. Outages and throttling are not
 * this: they still throw their own exceptions and are retried.
 */
class UndeliverableEmailException extends RuntimeException {

    UndeliverableEmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
