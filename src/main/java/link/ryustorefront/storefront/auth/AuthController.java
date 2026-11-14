package link.ryustorefront.storefront.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import link.ryustorefront.storefront.auth.AuthDtos.ApiException;
import link.ryustorefront.storefront.auth.AuthDtos.CsrfView;
import link.ryustorefront.storefront.auth.AuthDtos.CustomerView;
import link.ryustorefront.storefront.auth.AuthDtos.LoginRequest;
import link.ryustorefront.storefront.auth.AuthDtos.RegisterRequest;
import link.ryustorefront.storefront.auth.AuthDtos.SessionView;
import link.ryustorefront.storefront.auth.internal.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, logout and "who am I" over a server-side session. Every POST needs the CSRF
 * header (including register and login, which prevents login CSRF): call {@code GET /csrf} first.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** BCrypt ignores (or, in newer versions, rejects) anything past 72 bytes. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final CustomerRepository customers;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfRepository;

    AuthController(AuthenticationManager authenticationManager,
                   PasswordEncoder passwordEncoder,
                   CustomerRepository customers,
                   SessionAuthenticationStrategy sessionStrategy,
                   SecurityContextRepository contextRepository,
                   CsrfTokenRepository csrfRepository) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.customers = customers;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.csrfRepository = csrfRepository;
    }

    /** Creates the session if needed and returns its CSRF token. */
    @GetMapping("/csrf")
    CsrfView csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfRepository.loadToken(request);
        if (token == null) {
            token = csrfRepository.generateToken(request);
            csrfRepository.saveToken(token, request, response);
        }
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    ResponseEntity<SessionView> register(@Valid @RequestBody RegisterRequest body,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (utf8Length(body.password()) > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_failed",
                    "Check the highlighted fields.", Map.of("password", "too_long"));
        }
        CustomerRepository.Row row;
        try {
            row = customers.insert(normalise(body.email()), passwordEncoder.encode(body.password()), body.displayName());
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken",
                    "An account with this email already exists.", Map.of("email", "taken"));
        }
        // The hash is not passed on: it must never reach the serialised session.
        CustomerPrincipal principal = new CustomerPrincipal(
                row.id(), row.email(), row.displayName(), row.role(), null);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        return ResponseEntity.status(HttpStatus.CREATED).body(startSession(authentication, principal, request, response));
    }

    @PostMapping("/login")
    SessionView login(@Valid @RequestBody LoginRequest body,
                      HttpServletRequest request, HttpServletResponse response) {
        if (utf8Length(body.password()) > MAX_PASSWORD_BYTES) {
            throw invalidCredentials(); // cannot be a password we ever stored
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalise(body.email()), body.password()));
        } catch (BadCredentialsException e) {
            // Unknown email and wrong password are deliberately indistinguishable.
            throw invalidCredentials();
        } catch (InternalAuthenticationServiceException e) {
            log.error("Authentication backend failed", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                    "Something went wrong. Try again.", Map.of());
        }
        return startSession(authentication, (CustomerPrincipal) authentication.getPrincipal(), request, response);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CustomerView me(@AuthenticationPrincipal CustomerPrincipal principal) {
        return CustomerView.of(principal);
    }

    /**
     * Establishes the session: new session id (fixation protection), rotated CSRF token, security
     * context saved to the session. Returns the new CSRF token because the old one is now invalid.
     */
    private SessionView startSession(Authentication authentication, CustomerPrincipal principal,
                                     HttpServletRequest request, HttpServletResponse response) {
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.getContextHolderStrategy().setContext(context);
        contextRepository.saveContext(context, request, response);

        CsrfToken token = csrfRepository.loadToken(request);
        if (token == null) {
            token = csrfRepository.generateToken(request);
            csrfRepository.saveToken(token, request, response);
        }
        return new SessionView(CustomerView.of(principal), new CsrfView(token.getHeaderName(), token.getToken()));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                "Email or password is incorrect.", Map.of());
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
