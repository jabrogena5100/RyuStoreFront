package link.ryustorefront.storefront.auth.internal;

import java.util.Locale;
import link.ryustorefront.storefront.auth.CustomerPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads customers from Postgres. Its presence stops Boot generating an in-memory user. */
@Service
public class CustomerUserDetailsService implements UserDetailsService {

    private final CustomerRepository customers;

    public CustomerUserDetailsService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return customers.findByEmail(normalised)
                .map(row -> new CustomerPrincipal(
                        row.id(), row.email(), row.displayName(), row.role(), row.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("No such customer"));
    }
}
