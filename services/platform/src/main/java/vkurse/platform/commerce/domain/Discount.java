/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.money.Money;

/**
 * What a promo code takes off a price: a share of it, or a fixed amount.
 *
 * @param percent for {@link Kind#PERCENT}: 1 to 100
 * @param amount for {@link Kind#AMOUNT}: rubles
 */
@Embeddable
public record Discount(
        @Enumerated(EnumType.STRING) Kind kind,
        @Nullable Integer percent,
        @Nullable BigDecimal amount) {

    /** Makes a discount from what a client sent, keeping only the value its kind uses. */
    public static Discount of(Kind kind, @Nullable Integer percent, @Nullable BigDecimal amount) {
        return switch (kind) {
            case PERCENT -> {
                if (percent == null || percent < 1 || percent > 100) {
                    throw new BadRequestException("A percent discount is from 1 to 100");
                }
                yield new Discount(kind, percent, null);
            }
            case AMOUNT -> {
                if (amount == null || amount.signum() <= 0 || amount.compareTo(Offer.MAX_PRICE.value()) > 0) {
                    throw new BadRequestException("A fixed discount is more than zero");
                }
                yield new Discount(kind, null, Money.rub(amount).value());
            }
        };
    }

    /** How much this takes off that price: never more than the price itself. */
    public Money of(Money price) {
        var taken = switch (kind) {
            case PERCENT ->
                new Money(
                        price.value()
                                .multiply(BigDecimal.valueOf(percent == null ? 0 : percent))
                                .divide(BigDecimal.valueOf(100), Money.SCALE, RoundingMode.HALF_UP),
                        price.currency());
            case AMOUNT -> new Money(amount == null ? BigDecimal.ZERO : amount, price.currency());
        };
        return price.isAtLeast(taken) ? taken : price;
    }

    public enum Kind {
        PERCENT,
        AMOUNT
    }
}
