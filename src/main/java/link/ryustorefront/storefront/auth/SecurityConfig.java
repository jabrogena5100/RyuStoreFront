package link.ryustorefront.storefront.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Placeholder until phase 3 adds sessions and login: public read-only catalog and health,
 * everything else denied.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Controller errors (400, 404) are re-dispatched to /error to render the body; without
                // this, Security answers that internal dispatch with 403 and hides the real status.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated());
        return http.build();
    }
}
