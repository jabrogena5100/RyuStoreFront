package link.ryustorefront.storefront.auth;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in customer as stored in the server-side session. This class is the whole public
 * surface other modules need from {@code auth}: the customer id, email and role.
 *
 * <p>Sessions are serialised into Postgres (Spring Session JDBC), so this class is
 * {@link java.io.Serializable} (through {@link UserDetails}) and must not carry the password hash
 * once authentication is done: {@link #eraseCredentials()} clears it.
 */
public final class CustomerPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private final String displayName;
    private final String role;
    private String passwordHash;

    public CustomerPrincipal(long id, String email, String displayName, String role, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.passwordHash = passwordHash;
    }

    public long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }

    /** The database role: {@code customer} or {@code admin}. */
    public String role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CustomerPrincipal p && p.id == id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
