package link.ryustorefront.storefront.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Server-side sessions in Postgres (Spring Session JDBC), CSRF on every state-changing request, and
 * JSON 401/403 bodies so the frontend never receives an HTML login page or a redirect.
 *
 * <p>The CSRF token lives in the session and is handed out by {@code GET /api/auth/csrf}. The plain
 * (not XOR-masked) request handler is used on purpose: the token is only ever delivered in a JSON
 * response to a script, not embedded in compressed HTML, so the BREACH mitigation would add a second
 * token format for the client to handle without adding protection.
 */
@Configuration
class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        // Delegating encoder: new hashes are {bcrypt}..., and the prefix lets a later change of
        // algorithm verify old hashes while upgrading them.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** On login: change the session id (fixation) and rotate the CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CsrfTokenRepository csrfTokenRepository,
                                            SecurityContextRepository securityContextRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) -> writeError(
                                response, HttpServletResponse.SC_UNAUTHORIZED,
                                "unauthenticated", "Sign in to continue."))
                        .accessDeniedHandler((request, response, e) -> {
                            if (e instanceof CsrfException) {
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "csrf_invalid", "Missing or invalid CSRF token. Reload and try again.");
                            } else {
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "forbidden", "You do not have access to this.");
                            }
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/categories").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // Controller errors (400, 404) are re-dispatched to /error to render the body; without
                        // this, Security answers that internal dispatch with 403 and hides the real status.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        // POST to these still needs the CSRF header; they just do not need a signed-in user.
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        // The cart works for guests (CART cookie) and signed-in customers; CSRF still applies.
                        .requestMatchers("/api/cart", "/api/cart/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated());
        return http.build();
    }

    /** Only ever called with constant strings, so no JSON escaping is needed. */
    private static void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"fields\":{}}");
    }
}
