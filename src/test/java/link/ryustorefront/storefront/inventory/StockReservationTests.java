package link.ryustorefront.storefront.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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

/** Single-threaded behaviour of reserve / commit / release / expiry against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockReservationTests {

    private static final Duration TTL = Duration.ofMinutes(15);

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

    @Test
    void reservingTakesStockAndRecordsAnActiveReservation() {
        long variant = fx.variant(10);
        UUID order = fx.order();

        var made = reservations.reserve(order, Map.of(variant, 3), TTL);

        assertThat(made).hasSize(1);
        assertThat(made.getFirst().variantId()).isEqualTo(variant);
        assertThat(made.getFirst().quantity()).isEqualTo(3);
        assertThat(made.getFirst().expiresAt()).isAfter(java.time.Instant.now().plus(Duration.ofMinutes(14)));
        assertThat(fx.stock(variant)).isEqualTo(7);
        assertThat(fx.reservations(variant, "active")).isEqualTo(1);
    }

    @Test
    void reservingExactlyTheRemainingStockSucceedsAndOneMoreFails() {
        long variant = fx.variant(2);

        reservations.reserve(fx.order(), Map.of(variant, 2), TTL);

        assertThat(fx.stock(variant)).isZero();
        assertThatThrownBy(() -> reservations.reserve(fx.order(), Map.of(variant, 1), TTL))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(fx.stock(variant)).isZero();
    }

    @Test
    void aFailedLineRollsBackTheWholeReservation() {
        long plenty = fx.variant(10);
        long scarce = fx.variant(2);
        UUID order = fx.order();

        assertThatThrownBy(() -> reservations.reserve(order, Map.of(plenty, 1, scarce, 5), TTL))
                .isInstanceOfSatisfying(InsufficientStockException.class,
                        e -> assertThat(e.variantId()).isEqualTo(scarce));

        assertThat(fx.stock(plenty)).as("first line must be rolled back").isEqualTo(10);
        assertThat(fx.stock(scarce)).isEqualTo(2);
        assertThat(fx.reservationsForOrder(order)).isZero();
    }

    @Test
    void inactiveVariantsCannotBeReserved() {
        long variant = fx.variant(10);
        jdbc.sql("UPDATE variants SET active = false WHERE id = :id").param("id", variant).update();

        assertThatThrownBy(() -> reservations.reserve(fx.order(), Map.of(variant, 1), TTL))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void invalidRequestsAreRejectedBeforeTouchingStock() {
        long variant = fx.variant(10);
        UUID order = fx.order();

        assertThatThrownBy(() -> reservations.reserve(order, Map.of(), TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.reserve(order, Map.of(variant, 0), TTL)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservations.reserve(order, Map.of(variant, 1), Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThat(fx.stock(variant)).isEqualTo(10);
    }

    @Test
    void releasingGivesStockBackExactlyOnce() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 4), TTL);

        assertThat(reservations.release(order)).isEqualTo(1);
        assertThat(reservations.release(order)).isZero();
        assertThat(reservations.release(order)).isZero();

        assertThat(fx.stock(variant)).isEqualTo(10);
        assertThat(fx.reservations(variant, "released")).isEqualTo(1);
    }

    @Test
    void committedReservationsKeepTheirStockAndCannotBeReleased() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 4), TTL);

        assertThat(reservations.commit(order)).isEqualTo(1);
        assertThat(reservations.commit(order)).as("idempotent").isZero();
        assertThat(reservations.release(order)).isZero();

        assertThat(fx.stock(variant)).isEqualTo(6);
        assertThat(fx.reservations(variant, "committed")).isEqualTo(1);
    }

    @Test
    void commitAfterReleaseDoesNothing() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 4), TTL);
        reservations.release(order);

        assertThat(reservations.commit(order)).isZero();
        assertThat(fx.stock(variant)).isEqualTo(10);
    }

    @Test
    void expiredReservationsAreReleasedAndFreshOnesAreNot() {
        long variant = fx.variant(10);
        UUID expiredOrder = fx.order();
        reservations.reserve(expiredOrder, Map.of(variant, 3), TTL);
        fx.expireAllActive();
        UUID freshOrder = fx.order();
        reservations.reserve(freshOrder, Map.of(variant, 2), TTL);

        assertThat(reservations.releaseExpired(100)).isEqualTo(1);
        assertThat(reservations.releaseExpired(100)).as("second sweep finds nothing").isZero();

        assertThat(fx.stock(variant)).isEqualTo(8); // 10 - 3 + 3 - 2
        assertThat(fx.reservations(variant, "released")).isEqualTo(1);
        assertThat(fx.reservations(variant, "active")).isEqualTo(1);
    }

    @Test
    void sweepRespectsTheBatchSize() {
        long variant = fx.variant(10);
        for (int i = 0; i < 5; i++) {
            reservations.reserve(fx.order(), Map.of(variant, 1), TTL);
        }
        fx.expireAllActive();

        assertThat(reservations.releaseExpired(2)).isEqualTo(2);
        assertThat(fx.stock(variant)).isEqualTo(7);
        assertThat(reservations.releaseExpired(100)).isEqualTo(3);
        assertThat(fx.stock(variant)).isEqualTo(10);
    }

    @Test
    void aReservationForTheSameVariantTwiceInOneOrderIsRejectedByTheDatabase() {
        long variant = fx.variant(10);
        UUID order = fx.order();
        reservations.reserve(order, Map.of(variant, 1), TTL);

        assertThatThrownBy(() -> reservations.reserve(order, Map.of(variant, 1), TTL))
                .hasMessageContaining("reservations_order_variant_uniq");
        assertThat(fx.stock(variant)).as("the failed attempt must not keep its stock").isEqualTo(9);
    }
}
