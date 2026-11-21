package link.ryustorefront.storefront.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * The SES client, only when {@code storefront.mail.transport=ses}. Credentials come from the SDK's default chain, which on
 * ECS is the task role (container credentials endpoint).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "storefront.mail.transport", havingValue = "ses")
class SesConfiguration {

    @Bean(destroyMethod = "close")
    SesV2Client sesV2Client(@Value("${storefront.mail.ses-region:us-west-2}") String region) {
        return SesV2Client.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }
}
