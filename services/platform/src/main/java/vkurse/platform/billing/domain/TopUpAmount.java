/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import java.math.BigDecimal;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.money.Money;

/** How much a school adds to its balance at once: 100 to 100 000 RUB. */
public record TopUpAmount(Money amount) {
    public static final Money MIN = Money.rub(100);
    public static final Money MAX = Money.rub(100_000);

    public TopUpAmount {
        if (!amount.isAtLeast(MIN) || !MAX.isAtLeast(amount)) {
            throw new BadRequestException("A top-up is " + MIN.value().toPlainString() + " to "
                    + MAX.value().toPlainString() + " RUB");
        }
    }

    public static TopUpAmount rub(BigDecimal value) {
        return new TopUpAmount(Money.rub(value));
    }
}
