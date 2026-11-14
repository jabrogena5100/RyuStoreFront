package link.ryustorefront.storefront.cart;

import static org.assertj.core.api.Assertions.assertThat;

import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.cart.CartTestClient.Response;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The deployed configuration: with STOREFRONT_COOKIE_SECURE=true the real CART Set-Cookie carries Secure. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"storefront.cart.cookie-secure=true", "server.servlet.session.cookie.secure=true"})
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecureCartCookieTests {

    @Autowired
    Environment env;
    @Autowired
    JdbcClient jdbc;

    @AfterAll
    void cleanUp() {
        new CartFixtures(jdbc).cleanUp();
    }

    @Test
    void cartCookieIsSecureHttpOnlyAndLax() throws Exception {
        CartFixtures.Item item = new CartFixtures(jdbc).variant(5, 12990);
        CartTestClient b = CartTestClient.guest("http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class));

        Response r = b.put("/api/cart/items/" + item.sku(), CartTestClient.qty(1));

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.setCookie("CART")).as("Set-Cookie headers: %s", r.headers().allValues("set-cookie")).isNotNull();
        assertThat(CartTestClient.attributes(r.setCookie("CART"))).contains("secure", "httponly", "samesite=lax", "path=/");
    }
}
