package link.ryustorefront.storefront.notifications;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Starts Mailpit for the tests that import it and points the application's SMTP settings at it. */
@TestConfiguration(proxyBeanMethods = false)
class MailpitConfiguration {

    @Bean
    MailpitContainer mailpitContainer() {
        return new MailpitContainer();
    }

    @Bean
    DynamicPropertyRegistrar mailpitProperties(MailpitContainer mailpit) {
        return registry -> {
            registry.add("spring.mail.host", mailpit::getHost);
            registry.add("spring.mail.port", () -> mailpit.getMappedPort(MailpitContainer.SMTP_PORT));
        };
    }
}
