/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import java.math.BigDecimal;
import vkurse.core.money.Money;

/** A money amount: {@code value} with two decimals, and the currency. */
public record PriceResponse(BigDecimal value, Money.Currency currency) {

    public static PriceResponse of(Money money) {
        return new PriceResponse(money.value(), money.currency());
    }
}
