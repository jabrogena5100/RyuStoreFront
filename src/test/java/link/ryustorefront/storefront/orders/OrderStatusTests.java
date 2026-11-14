package link.ryustorefront.storefront.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every ordered pair of statuses (6 x 6 = 36), checked against a table written out here in full and
 * independently of the one in {@link OrderStatus}. If the rules change, both tables must change.
 */
class OrderStatusTests {

    /** The complete list of legal moves, as "from>to". Everything else is illegal. */
    static final Set<String> LEGAL = Set.of(
            "PENDING>PAID", "PENDING>CANCELLED",
            "PAID>FULFILLED", "PAID>REFUNDED",
            "FULFILLED>SHIPPED", "FULFILLED>REFUNDED",
            "SHIPPED>REFUNDED");

    @Test
    void allThirtySixPairsMatchTheTable() {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                checked++;
                boolean expected = LEGAL.contains(from + ">" + to);
                if (from.canTransitionTo(to) != expected) {
                    mismatches.add(from + ">" + to + " expected " + expected);
                }
            }
        }
        assertThat(checked).isEqualTo(36);
        assertThat(mismatches).isEmpty();
    }

    @Test
    void theTableHasExactlySevenLegalMoves() {
        long legal = 0;
        for (OrderStatus from : OrderStatus.values()) {
            legal += from.allowedNext().size();
        }
        assertThat(legal).isEqualTo(LEGAL.size()).isEqualTo(7);
    }

    @Test
    void staysInPlaceIsNeverATransition() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(status)).as(status.name()).isFalse();
        }
    }

    @Test
    void nothingEverMovesBackToPending() {
        for (OrderStatus from : OrderStatus.values()) {
            assertThat(from.canTransitionTo(OrderStatus.PENDING)).as(from.name()).isFalse();
        }
    }

    @Test
    void cancelledAndRefundedAreTerminalAndNothingElseIs() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        for (OrderStatus status : EnumSet.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.FULFILLED, OrderStatus.SHIPPED)) {
            assertThat(status.isTerminal()).as(status.name()).isFalse();
        }
    }

    @Test
    void aPaidOrderCanOnlyBeRefundedNotCancelled() {
        assertThat(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.FULFILLED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void everyStatusIsReachableFromPending() {
        Set<OrderStatus> reached = EnumSet.of(OrderStatus.PENDING);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (OrderStatus from : List.copyOf(reached)) {
                grew |= reached.addAll(from.allowedNext());
            }
        }
        assertThat(reached).containsExactlyInAnyOrder(OrderStatus.values());
    }

    @Test
    void databaseValuesRoundTripAndMatchTheSchemaCheck() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderStatus.fromDb(status.dbValue())).isEqualTo(status);
            assertThat(status.dbValue()).isEqualTo(status.name().toLowerCase());
        }
    }
}
