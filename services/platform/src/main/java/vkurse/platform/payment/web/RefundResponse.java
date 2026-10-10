/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.payment.domain.Refund;

public record RefundResponse(
        UUID id,
        Instant createdAt,
        UUID paymentId,
        MoneyResponse amount,
        @Nullable String reason,
        UUID requestedBy,
        Refund.Status status) {

    public static RefundResponse of(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getCreatedAt(),
                refund.getPaymentId(),
                MoneyResponse.of(refund.getAmount()),
                refund.getReason(),
                refund.getRequestedBy(),
                refund.getStatus());
    }
}
