package link.ryustorefront.storefront.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import link.ryustorefront.storefront.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Extending a hold (done when a payment page is created) only ever moves it later, and only while active. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockHoldExtensionTests {

    @Autowired
    StockReservations reservations;
    @Autowired
    JdbcClient jdbc;

    InventoryFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new InventoryFixtures(jdbc);
        fx.cleanUp();
    }

    @AfterEach
    void tearDown() {
        fx.cleanUp();
    }

    private long minutesUntilExpiry(UUID order, long variant) {
        return jdbc.sql("SELECT round(EXTRACT(EPOCH FROM (expires_at - now())) / 60) FROM reservations "
                        + "WHERE order_id = :o AND variant_id = :v")
                .param("o", order).param("v", variant).query(Long.class).single();
    }

    @Test
    void anActiveHoldIsMovedLater() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 2), Duration.ofMinutes(10));

        int extended = reservations.extendHold(order, Instant.now().plus(Duration.ofMinutes(40)));

        assertThat(extended).isEqualTo(1);
        assertThat(minutesUntilExpiry(order, variant)).isBetween(39L, 40L);
        assertThat(fx.stock(variant)).as("extending takes no more stock").isEqualTo(8);
    }

    @Test
    void everyReservationOfTheOrderIsExtended() {
        long a = fx.variant(10);
        long b = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(a, 1, b, 1), Duration.ofMinutes(10));

        int extended = reservations.extendHold(order, Instant.now().plus(Duration.ofMinutes(40)));

        assertThat(extended).isEqualTo(2);
        assertThat(minutesUntilExpiry(order, a)).isBetween(39L, 40L);
        assertThat(minutesUntilExpiry(order, b)).isBetween(39L, 40L);
    }

    @Test
    void aHoldIsNeverShortened() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 1), Duration.ofMinutes(30));

        int extended = reservations.extendHold(order, Instant.now().plus(Duration.ofMinutes(5)));

        assertThat(extended).isZero();
        assertThat(minutesUntilExpiry(order, variant)).isBetween(29L, 30L);
    }

    @Test
    void committedAndReleasedReservationsAreLeftAlone() {
        long committedVariant = fx.variant(10);
        UUID committed = fx.order();
        reservations.reserve(committed, Map.of(committedVariant, 1), Duration.ofMinutes(10));
        reservations.commit(committed);
        long releasedVariant = fx.variant(10);
        UUID released = fx.order();
        reservations.reserve(released, Map.of(releasedVariant, 1), Duration.ofMinutes(10));
        reservations.release(released);

        Instant later = Instant.now().plus(Duration.ofMinutes(40));

        assertThat(reservations.extendHold(committed, later)).isZero();
        assertThat(reservations.extendHold(released, later)).isZero();
        assertThat(minutesUntilExpiry(committed, committedVariant)).isBetween(9L, 10L);
    }

    @Test
    void anOrderWithNoReservationsExtendsNothing() {
        assertThat(reservations.extendHold(fx.order(), Instant.now().plus(Duration.ofMinutes(40)))).isZero();
    }
}
