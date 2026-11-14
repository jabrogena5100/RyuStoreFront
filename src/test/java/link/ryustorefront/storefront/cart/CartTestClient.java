package link.ryustorefront.storefront.cart;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A browser stand-in over the plain JDK client. It has no cookie manager: it keeps the SESSION and
 * CART cookies itself, from the raw Set-Cookie headers the server really sent (honouring Max-Age=0).
 */
final class CartTestClient {

    record Response(int status, String body, HttpHeaders headers) {

        String setCookie(String name) {
            return headers.allValues("set-cookie").stream().filter(c -> c.startsWith(name + "="))
                    .findFirst().orElse(null);
        }

        String str(String path) {
            return JsonPath.read(body, path);
        }

        long num(String path) {
            return JsonPath.<Number>read(body, path).longValue();
        }

        boolean bool(String path) {
            return JsonPath.<Boolean>read(body, path);
        }

        /** sku to quantity for the lines of a cart response. */
        Map<String, Integer> quantities() {
            List<Map<String, Object>> lines = JsonPath.read(body, "$.lines");
            return lines.stream().collect(Collectors.toMap(l -> (String) l.get("sku"), l -> ((Number) l.get("quantity")).intValue()));
        }
    }

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String base;
    final Map<String, String> cookies = new ConcurrentHashMap<>();
    volatile String csrfToken;

    CartTestClient(String base) {
        this.base = base;
    }

    Response get(String path) throws Exception {
        return send("GET", path, null, csrfToken, false);
    }

    Response put(String path, String json) throws Exception {
        return send("PUT", path, json, csrfToken, true);
    }

    Response delete(String path) throws Exception {
        return send("DELETE", path, null, csrfToken, true);
    }

    Response putWithoutCsrf(String path, String json) throws Exception {
        return send("PUT", path, json, null, true);
    }

    Response deleteWithoutCsrf(String path) throws Exception {
        return send("DELETE", path, null, null, true);
    }

    Response fetchCsrf() throws Exception {
        Response r = get("/api/auth/csrf");
        csrfToken = r.str("$.token");
        return r;
    }

    /** A guest browser that already holds a session and CSRF token, as the real frontend would. */
    static CartTestClient guest(String base) throws Exception {
        CartTestClient c = new CartTestClient(base);
        c.fetchCsrf();
        return c;
    }

    Response register(String email, String password) throws Exception {
        fetchCsrf();
        Response r = send("POST", "/api/auth/register", credentials(email, password), csrfToken, true);
        if (r.status() == 201) {
            csrfToken = r.str("$.csrf.token");
        }
        return r;
    }

    Response login(String email, String password) throws Exception {
        fetchCsrf();
        Response r = send("POST", "/api/auth/login", credentials(email, password), csrfToken, true);
        if (r.status() == 200) {
            csrfToken = r.str("$.csrf.token");
        }
        return r;
    }

    static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    static String qty(int quantity) {
        return "{\"quantity\":" + quantity + "}";
    }

    /** Lowercased attributes of a Set-Cookie header, e.g. [cart=..., path=/, max-age=2592000, httponly]. */
    static List<String> attributes(String setCookie) {
        return List.of(setCookie.split(";")).stream().map(a -> a.trim().toLowerCase(Locale.ROOT)).toList();
    }

    private Response send(String method, String path, String json, String csrf, boolean hasBody) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (json == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json));
        }
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
        }
        if (csrf != null) {
            request.header("X-CSRF-TOKEN", csrf);
        }
        HttpResponse<String> raw = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        Response response = new Response(raw.statusCode(), raw.body(), raw.headers());
        track(response);
        return response;
    }

    private void track(Response response) {
        for (String header : response.headers().allValues("set-cookie")) {
            int eq = header.indexOf('=');
            String name = header.substring(0, eq);
            if (!name.equals("SESSION") && !name.equals("CART")) {
                continue;
            }
            int end = header.indexOf(';') < 0 ? header.length() : header.indexOf(';');
            String value = header.substring(eq + 1, end);
            if (value.isEmpty() || attributes(header).contains("max-age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
