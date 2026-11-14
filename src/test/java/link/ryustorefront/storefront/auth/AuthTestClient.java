package link.ryustorefront.storefront.auth;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * A browser stand-in over the plain JDK client. It has no cookie manager on purpose: the test
 * reads the raw {@code Set-Cookie} headers the server really sent, and replays the cookie itself.
 */
final class AuthTestClient {

    static final String SESSION_COOKIE = "SESSION";

    record Response(int status, String body, HttpHeaders headers) {

        /** The raw Set-Cookie header for the session cookie, or null if the response set none. */
        String sessionSetCookie() {
            return headers.allValues("set-cookie").stream()
                    .filter(c -> c.startsWith(SESSION_COOKIE + "="))
                    .findFirst().orElse(null);
        }

        String json(String path) {
            return JsonPath.read(body, path);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base;

    /** Raw value of the SESSION cookie as the server sent it; null when we hold none. */
    String sessionCookie;
    String csrfToken;

    AuthTestClient(String base) {
        this.base = base;
    }

    Response get(String path) throws Exception {
        return send("GET", path, null, false);
    }

    /** POST with the CSRF header we currently hold (if any). */
    Response post(String path, String json) throws Exception {
        return send("POST", path, json, true);
    }

    /** POST with no CSRF header at all. */
    Response postWithoutCsrf(String path, String json) throws Exception {
        return send("POST", path, json, false);
    }

    /** POST with an explicit CSRF header value. */
    Response postWithCsrf(String path, String json, String token) throws Exception {
        return send("POST", path, json, token);
    }

    Response fetchCsrf() throws Exception {
        Response response = get("/api/auth/csrf");
        csrfToken = response.json("$.token");
        return response;
    }

    Response register(String email, String password) throws Exception {
        fetchCsrf();
        Response response = post("/api/auth/register", credentials(email, password));
        if (response.status() == 201) {
            csrfToken = response.json("$.csrf.token");
        }
        return response;
    }

    Response login(String email, String password) throws Exception {
        fetchCsrf();
        Response response = post("/api/auth/login", credentials(email, password));
        if (response.status() == 200) {
            csrfToken = response.json("$.csrf.token");
        }
        return response;
    }

    /** The session id the cookie encodes (Spring Session base64-encodes the id). */
    String sessionId() {
        return new String(Base64.getDecoder().decode(sessionCookie), StandardCharsets.UTF_8);
    }

    static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** Lowercased attributes of a Set-Cookie header, e.g. [session=..., path=/, httponly, samesite=lax]. */
    static List<String> attributes(String setCookie) {
        return List.of(setCookie.split(";")).stream().map(a -> a.trim().toLowerCase(Locale.ROOT)).toList();
    }

    private Response send(String method, String path, String json, boolean withCsrf) throws Exception {
        return send(method, path, json, withCsrf ? csrfToken : null);
    }

    private Response send(String method, String path, String json, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (json == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        if (sessionCookie != null) {
            request.header("Cookie", SESSION_COOKIE + "=" + sessionCookie);
        }
        if (csrf != null) {
            request.header("X-CSRF-TOKEN", csrf);
        }
        HttpResponse<String> raw = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        Response response = new Response(raw.statusCode(), raw.body(), raw.headers());
        trackCookie(response);
        return response;
    }

    private void trackCookie(Response response) {
        String setCookie = response.sessionSetCookie();
        if (setCookie == null) {
            return;
        }
        String value = setCookie.substring(SESSION_COOKIE.length() + 1, setCookie.indexOf(';') < 0
                ? setCookie.length() : setCookie.indexOf(';'));
        boolean expired = value.isEmpty() || attributes(setCookie).contains("max-age=0");
        sessionCookie = expired ? null : value;
    }
}
