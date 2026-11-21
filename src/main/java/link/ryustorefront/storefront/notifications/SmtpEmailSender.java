package link.ryustorefront.storefront.notifications;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** SMTP through Spring's {@link JavaMailSender} (Mailpit from compose.yaml locally). The default transport. */
@Component
@ConditionalOnProperty(name = "storefront.mail.transport", havingValue = "smtp", matchIfMissing = true)
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mail;

    SmtpEmailSender(JavaMailSender mail) {
        this.mail = mail;
    }

    @Override
    public void send(String from, String to, EmailContent content) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(content.subject());
            helper.setText(content.text(), content.html());
            mail.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the email", e);
        }
    }
}
