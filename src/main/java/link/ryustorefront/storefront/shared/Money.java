package link.ryustorefront.storefront.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money as integer minor units (for example cents) plus a currency.
 * Never uses floating point. Arithmetic is overflow-checked and refuses to mix currencies.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return of(0, currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money times(int quantity) {
        return new Money(Math.multiplyExact(minorUnits, (long) quantity), currency);
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    /** For example "129.90 USD", or "5000 JPY" for a currency without minor units. */
    @Override
    public String toString() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(minorUnits, digits).toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }
}
