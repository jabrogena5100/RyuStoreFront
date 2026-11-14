package link.ryustorefront.storefront.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        Money a = Money.of(12_990, "USD");
        Money b = Money.of(500, "USD");

        assertThat(a.plus(b)).isEqualTo(Money.of(13_490, "USD"));
        assertThat(a.minus(b)).isEqualTo(Money.of(12_490, "USD"));
    }

    @Test
    void multipliesByQuantity() {
        assertThat(Money.of(8_999, "USD").times(3)).isEqualTo(Money.of(26_997, "USD"));
    }

    @Test
    void sumOfCentsHasNoFloatingPointDrift() {
        Money total = Money.zero("USD");
        for (int i = 0; i < 10; i++) {
            total = total.plus(Money.of(10, "USD"));
        }
        assertThat(total).isEqualTo(Money.of(100, "USD"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "USD").plus(Money.of(100, "EUR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency mismatch: USD vs EUR");
        assertThatThrownBy(() -> Money.of(100, "USD").compareTo(Money.of(100, "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsLoudlyOnOverflow() {
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1, "USD")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Money.of(Long.MAX_VALUE, "USD").times(2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void formatsUsingTheCurrencyFractionDigits() {
        assertThat(Money.of(12_990, "USD")).hasToString("129.90 USD");
        assertThat(Money.of(5, "USD")).hasToString("0.05 USD");
        assertThat(Money.of(-250, "USD")).hasToString("-2.50 USD");
        assertThat(Money.of(5_000, "JPY")).hasToString("5000 JPY");
    }

    @Test
    void comparesAndDetectsNegative() {
        assertThat(Money.of(100, "USD")).isLessThan(Money.of(200, "USD"));
        assertThat(Money.of(-1, "USD").isNegative()).isTrue();
        assertThat(Money.zero("USD").isNegative()).isFalse();
    }

    @Test
    void rejectsUnknownCurrencyCode() {
        assertThatThrownBy(() -> Money.of(100, "ZZZ")).isInstanceOf(IllegalArgumentException.class);
    }
}
