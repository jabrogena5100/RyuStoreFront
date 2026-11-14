package link.ryustorefront.storefront.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import link.ryustorefront.storefront.auth.AuthTestClient.Response;
import link.ryustorefront.storefront.auth.internal.CustomerUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Real HTTP against the real server and a real Postgres. Cookies and CSRF behaviour are read from
 * the actual headers and bodies the handlers produce; session and customer state is checked with
 * independent SQL. Each test registers its own random email, so tests do not depend on each other.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthApiTests {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    Environment env;
    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;

    private AuthTestClient browser() {
        return new AuthTestClient("http://localhost:" + env.getRequiredProperty("local.server.port", Integer.class));
    }

    private static String uniqueEmail() {
        return "buyer-" + UUID.randomUUID() + "@example.com";
    }

    private long customersWithEmail(String email) {
        return jdbc.sql("SELECT count(*) FROM customers WHERE email = :e").param("e", email).query(Long.class).single();
    }

    private long sessionRows(String sessionId) {
        return jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = :id")
                .param("id", sessionId).query(Long.class).single();
    }

    // ---- cookie flags and anonymous behaviour -------------------------------------------------

    @Test
    void csrfEndpointIssuesAnHttpOnlyLaxSessionCookieAndAToken() throws Exception {
        AuthTestClient b = browser();

        Response r = b.get("/api/auth/csrf");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.json("$.headerName")).isEqualTo("X-CSRF-TOKEN");
        assertThat(r.json("$.token")).isNotBlank();
        String cookie = r.sessionSetCookie();
        assertThat(cookie).as("Set-Cookie headers: %s", r.headers().allValues("set-cookie")).isNotNull();
        assertThat(AuthTestClient.attributes(cookie)).contains("httponly", "samesite=lax", "path=/");
        assertThat(AuthTestClient.attributes(cookie)).as("Secure is off unless STOREFRONT_COOKIE_SECURE is set")
                .doesNotContain("secure");
        assertThat(sessionRows(b.sessionId())).isEqualTo(1);
    }

    @Test
    void browsingTheCatalogAnonymouslyCreatesNoSession() throws Exception {
        AuthTestClient b = browser();

        Response r = b.get("/api/products");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.headers().allValues("set-cookie")).isEmpty();
    }

    @Test
    void anonymousMeIsAJson401NotARedirect() throws Exception {
        Response r = browser().get("/api/auth/me");

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(r.json("$.code")).isEqualTo("unauthenticated");
        assertThat(r.headers().firstValue("location")).isEmpty();
    }

    @Test
    void theGeneratedInMemoryUserIsGone() {
        // Boot only generates a user (and logs "Using generated security password") when no
        // UserDetailsService bean exists; ours must be the only one.
        Map<String, UserDetailsService> beans = context.getBeansOfType(UserDetailsService.class);

        assertThat(beans).hasSize(1);
        assertThat(beans.values().iterator().next()).isInstanceOf(CustomerUserDetailsService.class);
    }

    // ---- registration -------------------------------------------------------------------------

    @Test
    void registerCreatesACustomerStoresABcryptHashAndSignsIn() throws Exception {
        AuthTestClient b = browser();
        String email = uniqueEmail();

        Response r = b.register(email, PASSWORD);

        assertThat(r.status()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(r.body(), "$.customer.id")).isPositive();
        assertThat(r.json("$.customer.email")).isEqualTo(email);
        assertThat(r.json("$.customer.role")).isEqualTo("customer");
        assertThat(r.body()).as("the response must never carry a hash").doesNotContainIgnoringCase("password")
                .doesNotContain("bcrypt");

        String stored = jdbc.sql("SELECT password_hash FROM customers WHERE email = :e")
                .param("e", email).query(String.class).single();
        assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored)).isTrue();

        Response me = b.get("/api/auth/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json("$.email")).isEqualTo(email);
    }

    @Test
    void theSerialisedSessionContainsTheCustomerButNotThePasswordHash() throws Exception {
        for (boolean viaLogin : new boolean[] {false, true}) {
            AuthTestClient b = browser();
            String email = uniqueEmail();
            b.register(email, PASSWORD);
            if (viaLogin) {
                b = browser();
                assertThat(b.login(email, PASSWORD).status()).isEqualTo(200);
            }

            List<byte[]> attributes = jdbc.sql("SELECT a.attribute_bytes FROM spring_session_attributes a "
                            + "JOIN spring_session s ON s.primary_id = a.session_primary_id WHERE s.session_id = :id")
                    .param("id", b.sessionId()).query((rs, n) -> rs.getBytes(1)).list();
            String all = String.join("|", attributes.stream()
                    .map(bytes -> new String(bytes, StandardCharsets.ISO_8859_1)).toList());

            assertThat(all).as("session via %s", viaLogin ? "login" : "register").contains(email);
            assertThat(all).doesNotContain("bcrypt").doesNotContain("$2a$").doesNotContain("$2b$");
        }
    }

    @Test
    void registeredEmailIsTrimmedAndLowercased() throws Exception {
        String bare = uniqueEmail();
        String messy = "  " + bare.toUpperCase() + "  ";

        Response r = browser().register(messy, PASSWORD);

        assertThat(r.status()).isEqualTo(201);
        assertThat(customersWithEmail(bare)).isEqualTo(1);
    }

    @Test
    void duplicateEmailInAnotherCaseIsRejectedWith409() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);

        Response r = browser().register(email.toUpperCase(), PASSWORD);

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.json("$.code")).isEqualTo("email_taken");
        assertThat(r.json("$.fields.email")).isEqualTo("taken");
        assertThat(customersWithEmail(email)).isEqualTo(1);
    }

    @Test
    void invalidRegistrationsReturnFieldCodesAndStoreNothing() throws Exception {
        record Case(String name, String email, String password, String field, String code) {
        }
        List<Case> cases = List.of(
                new Case("short password", uniqueEmail(), "short", "password", "length"),
                new Case("blank password", uniqueEmail(), "", "password", "required"),
                new Case("73-byte password", uniqueEmail(), "a".repeat(73), "password", "length"),
                new Case("74 bytes in 37 characters", uniqueEmail(), "é".repeat(37), "password", "too_long"),
                new Case("not an email", "not-an-email", PASSWORD, "email", "invalid_email"),
                new Case("blank email", "", PASSWORD, "email", "required"),
                new Case("non-ascii email", "josé-" + UUID.randomUUID() + "@example.com", PASSWORD, "email", "invalid_email"),
                new Case("email over 100 chars", "a".repeat(95) + "@example.com", PASSWORD, "email", "length"));

        for (Case c : cases) {
            AuthTestClient b = browser();
            Response r = b.register(c.email(), c.password());

            assertThat(r.status()).as(c.name()).isEqualTo(400);
            assertThat(r.json("$.code")).as(c.name()).isEqualTo("validation_failed");
            assertThat(r.json("$.fields." + c.field())).as(c.name()).isEqualTo(c.code());
            assertThat(customersWithEmail(c.email().trim().toLowerCase())).as(c.name()).isZero();
        }
    }

    @Test
    void malformedJsonIs400WithAStableCode() throws Exception {
        AuthTestClient b = browser();
        b.fetchCsrf();

        Response r = b.post("/api/auth/register", "{not json");

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json("$.code")).isEqualTo("malformed_request");
    }

    // ---- login, session fixation, logout ------------------------------------------------------

    @Test
    void loginSetsTheCookieWithTheRightFlagsAndRotatesTheSessionAndCsrfToken() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);

        AuthTestClient b = browser();
        Response csrf = b.fetchCsrf();
        String sessionBefore = b.sessionCookie;
        String idBefore = b.sessionId();
        String csrfBefore = b.csrfToken;
        assertThat(csrf.status()).isEqualTo(200);

        Response login = b.post("/api/auth/login", AuthTestClient.credentials(email, PASSWORD));

        assertThat(login.status()).isEqualTo(200);
        String setCookie = login.sessionSetCookie();
        assertThat(setCookie).as("login must send a fresh session cookie: %s", login.headers().allValues("set-cookie"))
                .isNotNull();
        assertThat(AuthTestClient.attributes(setCookie)).contains("httponly", "samesite=lax", "path=/");
        assertThat(b.sessionCookie).as("session fixation: the id must change on login").isNotEqualTo(sessionBefore);
        assertThat(sessionRows(idBefore)).as("the pre-login session id must be dead").isZero();
        assertThat(sessionRows(b.sessionId())).isEqualTo(1);
        String csrfAfter = login.json("$.csrf.token");
        assertThat(csrfAfter).as("CSRF token rotates on login").isNotEqualTo(csrfBefore);

        // An attacker who planted the old cookie gets nothing.
        AuthTestClient attacker = browser();
        attacker.sessionCookie = sessionBefore;
        assertThat(attacker.get("/api/auth/me").status()).isEqualTo(401);

        // The victim is signed in; the old CSRF token no longer works, the new one does.
        assertThat(b.get("/api/auth/me").status()).isEqualTo(200);
        Response oldToken = b.postWithCsrf("/api/auth/logout", null, csrfBefore);
        assertThat(oldToken.status()).isEqualTo(403);
        assertThat(oldToken.json("$.code")).isEqualTo("csrf_invalid");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameIndistinguishable401() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);

        AuthTestClient wrong = browser();
        Response wrongPassword = wrong.login(email, "not the password");
        Response unknown = browser().login(uniqueEmail(), PASSWORD);

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknown.body());
        assertThat(wrongPassword.json("$.code")).isEqualTo("invalid_credentials");
        assertThat(wrong.get("/api/auth/me").status()).as("a failed login must not sign anyone in").isEqualTo(401);
    }

    @Test
    void loginAcceptsTheEmailInAnyCase() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);

        assertThat(browser().login(email.toUpperCase(), PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void aPasswordOver72BytesIsA401NotAServerError() throws Exception {
        Response r = browser().login(uniqueEmail(), "a".repeat(200));

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.json("$.code")).isEqualTo("invalid_credentials");
    }

    @Test
    void loginWithMissingFieldsIs400() throws Exception {
        AuthTestClient b = browser();
        b.fetchCsrf();

        Response r = b.post("/api/auth/login", "{}");

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.json("$.fields.email")).isEqualTo("required");
        assertThat(r.json("$.fields.password")).isEqualTo("required");
    }

    @Test
    void logoutDestroysTheSessionInTheDatabaseAndExpiresTheCookie() throws Exception {
        String email = uniqueEmail();
        AuthTestClient b = browser();
        b.register(email, PASSWORD);
        String oldCookie = b.sessionCookie;
        String id = b.sessionId();
        assertThat(sessionRows(id)).isEqualTo(1);

        Response logout = b.post("/api/auth/logout", null);

        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.sessionSetCookie()).as("logout must expire the cookie: %s", logout.headers().allValues("set-cookie"))
                .isNotNull();
        assertThat(AuthTestClient.attributes(logout.sessionSetCookie())).contains("max-age=0");
        assertThat(sessionRows(id)).isZero();

        AuthTestClient replay = browser();
        replay.sessionCookie = oldCookie;
        assertThat(replay.get("/api/auth/me").status()).as("the old cookie is worthless after logout").isEqualTo(401);
    }

    // ---- CSRF ---------------------------------------------------------------------------------

    @Test
    void postsWithoutAValidCsrfTokenAreRejectedWithAJson403() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);
        String body = AuthTestClient.credentials(email, PASSWORD);

        AuthTestClient noSession = browser();
        Response a = noSession.postWithoutCsrf("/api/auth/login", body);

        AuthTestClient noHeader = browser();
        noHeader.fetchCsrf();
        Response b = noHeader.postWithoutCsrf("/api/auth/login", body);

        AuthTestClient garbage = browser();
        garbage.fetchCsrf();
        Response c = garbage.postWithCsrf("/api/auth/login", body, "garbage");

        AuthTestClient mine = browser();
        mine.fetchCsrf();
        AuthTestClient theirs = browser();
        theirs.fetchCsrf();
        Response d = mine.postWithCsrf("/api/auth/login", body, theirs.csrfToken);

        for (Response r : List.of(a, b, c, d)) {
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.json("$.code")).isEqualTo("csrf_invalid");
        }
        assertThat(noHeader.get("/api/auth/me").status()).as("a rejected login must not sign in").isEqualTo(401);
    }

    @Test
    void registerWithoutCsrfCreatesNoCustomer() throws Exception {
        String email = uniqueEmail();
        AuthTestClient b = browser();
        b.fetchCsrf();

        Response r = b.postWithoutCsrf("/api/auth/register", AuthTestClient.credentials(email, PASSWORD));

        assertThat(r.status()).isEqualTo(403);
        assertThat(customersWithEmail(email)).isZero();
    }

    @Test
    void safeMethodsNeedNoCsrfToken() throws Exception {
        assertThat(browser().get("/api/products").status()).isEqualTo(200);
    }

    // ---- roles --------------------------------------------------------------------------------

    @Test
    void adminAreaIs401ForGuestsAnd403ForCustomersAndOpenForAdmins() throws Exception {
        String email = uniqueEmail();
        assertThat(browser().register(email, PASSWORD).status()).isEqualTo(201);

        assertThat(browser().get("/api/admin/anything").status()).isEqualTo(401);

        AuthTestClient customer = browser();
        assertThat(customer.login(email, PASSWORD).status()).isEqualTo(200);
        Response denied = customer.get("/api/admin/anything");
        assertThat(denied.status()).isEqualTo(403);
        assertThat(denied.json("$.code")).isEqualTo("forbidden");

        jdbc.sql("UPDATE customers SET role = 'admin' WHERE email = :e").param("e", email).update();
        AuthTestClient admin = browser();
        Response login = admin.login(email, PASSWORD);
        assertThat(login.json("$.customer.role")).isEqualTo("admin");
        // No admin controller exists yet: 404 means authorisation passed and routing found nothing.
        assertThat(admin.get("/api/admin/anything").status()).isEqualTo(404);
    }

    @Test
    void roleIsAlwaysCustomerOnRegistration() throws Exception {
        String email = uniqueEmail();
        AuthTestClient b = browser();
        b.fetchCsrf();

        Response r = b.post("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"admin\"}");

        assertThat(r.status()).isEqualTo(201);
        String role = jdbc.sql("SELECT role FROM customers WHERE email = :e").param("e", email).query(String.class).single();
        assertThat(role).isEqualTo("customer");
    }
}
