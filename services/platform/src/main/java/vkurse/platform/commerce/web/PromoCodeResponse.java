/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.commerce.domain.Discount;
import vkurse.platform.commerce.domain.PromoCode;

/** A promo code as the staff see it. An empty {@code offerIds} means every offer of the school. */
public record PromoCodeResponse(
        UUID id,
        Instant createdAt,
        String code,
        DiscountResponse discount,
        @Nullable Instant validUntil,
        @Nullable Integer maxUses,
        int usedCount,
        List<UUID> offerIds,
        boolean active) {

    public static PromoCodeResponse of(PromoCode promoCode) {
        var discount = promoCode.getDiscount();
        return new PromoCodeResponse(
                promoCode.getId(),
                promoCode.getCreatedAt(),
                promoCode.getCode().value(),
                new DiscountResponse(discount.kind(), discount.percent(), discount.amount()),
                promoCode.getValidUntil(),
                promoCode.getMaxUses(),
                promoCode.getUsedCount(),
                promoCode.getScope().offerIds(),
                promoCode.isActive());
    }

    /** {@code percent} goes with {@code PERCENT}, {@code amount} (rubles) with {@code AMOUNT}. */
    public record DiscountResponse(
            Discount.Kind kind,
            @Nullable Integer percent,
            @Nullable BigDecimal amount) {}
}
