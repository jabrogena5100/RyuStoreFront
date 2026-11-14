package link.ryustorefront.storefront.auth.internal;

import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Plain SQL over {@code customers}. The database enforces lowercase, unique emails. */
@Repository
public class CustomerRepository {

    public record Row(long id, String email, String passwordHash, String displayName, String role) {
    }

    private static final String COLUMNS = "id, email, password_hash, display_name, role";

    private static final RowMapper<Row> MAPPER = (rs, rowNum) -> new Row(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getString("role"));

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Row> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM customers WHERE email = :email")
                .param("email", email)
                .query(MAPPER)
                .optional();
    }

    /**
     * Inserts a customer with the default {@code customer} role. A second insert of the same email
     * throws {@link org.springframework.dao.DuplicateKeyException} from the
     * {@code customers_email_uniq} constraint; there is deliberately no check-then-insert.
     */
    public Row insert(String email, String passwordHash, String displayName) {
        return jdbc.sql("INSERT INTO customers (email, password_hash, display_name) "
                        + "VALUES (:email, :hash, :name) RETURNING " + COLUMNS)
                .param("email", email)
                .param("hash", passwordHash)
                .param("name", displayName, Types.VARCHAR)
                .query(MAPPER)
                .single();
    }
}
