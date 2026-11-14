package link.ryustorefront.storefront.cart.internal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The guest cart cookie. The value is 32 random bytes (256 bits); only its SHA-256 is stored in
 * {@code carts.guest_token_hash}, so a database leak does not hand out carts. A random token needs
 * no signing key, and a forged or garbage value simply matches no cart.
 */
@Component
public class GuestCartCookie {

    public static final String NAME = "CART";

    /** 32 bytes as unpadded base64url is exactly 43 characters. Anything else is ignored, never looked up. */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SecureRandom random = new SecureRandom();
    private final boolean secure;
    private final Duration ttl;

    public GuestCartCookie(@Value("${storefront.cart.cookie-secure:false}") boolean secure,
                           @Value("${storefront.cart.guest-ttl-days:30}") int ttlDays) {
        this.secure = secure;
        this.ttl = Duration.ofDays(ttlDays);
    }

    public Optional<String> readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName()) && cookie.getValue() != null && VALID.matcher(cookie.getValue()).matches()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JDK", e);
        }
    }

    /** Set-Cookie header value that issues (or renews) the cookie. */
    public String issue(String token) {
        return base(token).maxAge(ttl).build().toString();
    }

    /** Set-Cookie header value that deletes the cookie. */
    public String expire() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Lax").path("/");
    }
}
