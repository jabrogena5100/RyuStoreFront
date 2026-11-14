package link.ryustorefront.storefront.auth;

import static org.assertj.core.api.Assertions.assertThat;

import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.auth.AuthTestClient.Response;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** The deployed configuration: with STOREFRONT_COOKIE_SECURE=true the real Set-Cookie carries Secure. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=true")
@Import(TestcontainersConfiguration.class)
class SecureCookieTests {

    @Autowired
    Environment env;

    @Test
    void sessionCookieIsSecureHttpOnlyAndLax() throws Exception {
        Response r = new AuthTestClient("http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class)).get("/api/auth/csrf");

        assertThat(r.sessionSetCookie()).as("Set-Cookie headers: %s", r.headers().allValues("set-cookie")).isNotNull();
        assertThat(AuthTestClient.attributes(r.sessionSetCookie()))
                .contains("secure", "httponly", "samesite=lax", "path=/");
    }
}
