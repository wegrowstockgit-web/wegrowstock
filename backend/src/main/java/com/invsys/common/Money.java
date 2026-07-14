package com.invsys.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Simple monetary amount for landed-cost allocation (currency-agnostic within a PO).
 */
public record Money(BigDecimal amount) {

    public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));

    public Money {
        Objects.requireNonNull(amount, "amount");
        amount = amount.setScale(4, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return new Money(value);
    }

    public static Money of(String value) {
        return of(new BigDecimal(value));
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor));
    }

    public Money divide(BigDecimal divisor) {
        if (divisor == null || divisor.signum() == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return new Money(amount.divide(divisor, 4, RoundingMode.HALF_UP));
    }

    public Money divide(int divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public BigDecimal toBigDecimal() {
        return amount;
    }
}
