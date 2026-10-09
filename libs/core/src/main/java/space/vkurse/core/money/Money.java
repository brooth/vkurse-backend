/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.money;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A money amount with its currency, always at scale 2. Stored as {@code <field>_value NUMERIC(19,2)} and {@code
 * <field>_currency VARCHAR(3)}. Fields of this type are named {@code amount} or {@code *Amount}.
 */
@Embeddable
public record Money(
        BigDecimal value, @Enumerated(EnumType.STRING) Currency currency) {

    public static final int SCALE = 2;
    public static final Money ZERO_RUB = rub(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(currency, "currency");
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money rub(BigDecimal value) {
        return new Money(value, Currency.RUB);
    }

    public static Money rub(String value) {
        return rub(new BigDecimal(value));
    }

    public static Money rub(long value) {
        return rub(BigDecimal.valueOf(value));
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(value.add(other.value), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(value.subtract(other.value), currency);
    }

    public boolean isAtLeast(Money other) {
        requireSameCurrency(other);
        return value.compareTo(other.value) >= 0;
    }

    @JsonIgnore
    public boolean isNegative() {
        return value.signum() < 0;
    }

    @JsonIgnore
    public boolean isZero() {
        return value.signum() == 0;
    }

    private void requireSameCurrency(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("Currency mismatch: " + currency + " and " + other.currency);
        }
    }

    /** Supported currencies. RUB only: the project launches in Russia. */
    public enum Currency {
        RUB
    }
}
