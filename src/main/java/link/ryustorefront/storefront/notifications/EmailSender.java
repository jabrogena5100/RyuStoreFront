package link.ryustorefront.storefront.notifications;

/**
 * Hands one finished email to a mail service. Chosen by {@code storefront.mail.transport}: {@code smtp} (default,
 * Mailpit locally) or {@code ses} (Amazon SES API in AWS, see {@link SesEmailSender}).
 *
 * <p>Throws if the service did not accept the message, so the outbox rolls back and retries the event.
 */
interface EmailSender {

    void send(String from, String to, EmailContent content);
}
