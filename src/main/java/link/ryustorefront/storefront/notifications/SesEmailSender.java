package link.ryustorefront.storefront.notifications;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent.Builder;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.MessageRejectedException;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Amazon SES (API v2, {@code SendEmail} with simple content: subject, text and HTML parts, UTF-8). Used in AWS, where the
 * task role allows {@code ses:SendEmail} on the verified domain; no SMTP credentials or access keys exist.
 *
 * <p>{@code MessageRejected} (in the sandbox: a recipient that is not verified) is permanent and becomes an
 * {@link UndeliverableEmailException}, so a stranger's purchase on the always-on demo still completes its other
 * {@code order.paid} work. Any other {@code SdkException} (throttling, a network error) propagates, so the outbox rolls
 * back the {@code sent_emails} row and retries the event with backoff.
 */
@Component
@ConditionalOnProperty(name = "storefront.mail.transport", havingValue = "ses")
class SesEmailSender implements EmailSender {

    private final SesV2Client ses;

    SesEmailSender(SesV2Client ses) {
        this.ses = ses;
    }

    @Override
    public void send(String from, String to, EmailContent content) {
        try {
            ses.sendEmail(request(from, to, content));
        } catch (MessageRejectedException e) {
            throw new UndeliverableEmailException("SES rejected the message: " + e.getMessage(), e);
        }
    }

    static SendEmailRequest request(String from, String to, EmailContent content) {
        Builder email = software.amazon.awssdk.services.sesv2.model.EmailContent.builder()
                .simple(Message.builder()
                        .subject(utf8(content.subject()))
                        .body(Body.builder().text(utf8(content.text())).html(utf8(content.html())).build())
                        .build());
        return SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(email.build())
                .build();
    }

    private static Content utf8(String data) {
        return Content.builder().data(data).charset("UTF-8").build();
    }
}
