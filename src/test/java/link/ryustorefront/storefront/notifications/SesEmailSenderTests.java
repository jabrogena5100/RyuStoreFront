package link.ryustorefront.storefront.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import link.ryustorefront.storefront.orders.OrderSummary;
import link.ryustorefront.storefront.orders.ShippingAddress;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

/**
 * The SES sender, checked on the request it really hands the SDK (not on a hand-written one): the confirmation email
 * built from an order goes through {@link SesEmailSender#send} into a capturing client.
 */
class SesEmailSenderTests {

    /** Records every SendEmail call; optionally fails like SES does for an unverified sandbox recipient. */
    static final class CapturingSes implements SesV2Client {
        final List<SendEmailRequest> sent = new ArrayList<>();
        boolean reject;

        @Override
        public SendEmailResponse sendEmail(SendEmailRequest request) {
            if (reject) {
                throw (SesV2Exception) SesV2Exception.builder().message("Email address is not verified.").statusCode(400).build();
            }
            sent.add(request);
            return SendEmailResponse.builder().messageId("test-1").build();
        }

        @Override
        public String serviceName() {
            return "ses";
        }

        @Override
        public void close() {
        }
    }

    private static final OrderSummary ORDER = new OrderSummary(UUID.fromString("1234abcd-0000-4000-8000-000000000001"),
            "paid", "buyer@example.com", "USD", 25_980, 800, 26_780,
            new ShippingAddress("Ada Buyer", "1 Main St", null, "Portland", "OR", "97201", "US"), null,
            Instant.parse("2026-11-14T10:00:00Z"),
            List.of(new OrderSummary.Line("SKU-1", "Chelsea Boot", "42", "black", 12_990, 2, 25_980)));

    @Test
    void sendsTheConfirmationAsSubjectTextAndHtmlInUtf8() {
        CapturingSes ses = new CapturingSes();
        EmailContent email = OrderConfirmationEmail.build(ORDER);

        new SesEmailSender(ses).send("orders@api.ryustorefront.com", "buyer@example.com", email);

        assertThat(ses.sent).hasSize(1);
        SendEmailRequest req = ses.sent.getFirst();
        assertThat(req.fromEmailAddress()).isEqualTo("orders@api.ryustorefront.com");
        assertThat(req.destination().toAddresses()).containsExactly("buyer@example.com");
        assertThat(req.destination().ccAddresses()).isEmpty();
        assertThat(req.destination().bccAddresses()).isEmpty();
        Message msg = req.content().simple();
        assertThat(msg.subject().data()).isEqualTo("Your order 1234ABCD is confirmed");
        assertThat(msg.body().text().data()).isEqualTo(email.text());
        assertThat(msg.body().html().data()).isEqualTo(email.html());
        assertThat(List.of(msg.subject().charset(), msg.body().text().charset(), msg.body().html().charset()))
                .containsOnly("UTF-8");
        assertThat(req.content().raw()).isNull();
        assertThat(req.content().template()).isNull();
    }

    @Test
    void aRejectedSendPropagatesSoTheOutboxRetries() {
        CapturingSes ses = new CapturingSes();
        ses.reject = true;

        assertThatThrownBy(() -> new SesEmailSender(ses).send("orders@api.ryustorefront.com", "buyer@example.com",
                OrderConfirmationEmail.build(ORDER)))
                .isInstanceOf(SesV2Exception.class)
                .hasMessageContaining("not verified");
    }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SesConfiguration.class, SesEmailSender.class, SmtpEmailSender.class)
            .withBean(JavaMailSender.class, JavaMailSenderImpl::new);

    @Test
    void smtpIsTheDefaultTransport() {
        context.run(ctx -> {
            assertThat(ctx).hasSingleBean(EmailSender.class).hasSingleBean(SmtpEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SesV2Client.class);
        });
    }

    @Test
    void sesTransportWiresTheSesClientAndSender() {
        context.withPropertyValues("storefront.mail.transport=ses", "storefront.mail.ses-region=us-west-2").run(ctx -> {
            assertThat(ctx).hasSingleBean(EmailSender.class).hasSingleBean(SesEmailSender.class);
            assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
            assertThat(ctx.getBean(SesV2Client.class).serviceClientConfiguration().region().id()).isEqualTo("us-west-2");
        });
    }
}
