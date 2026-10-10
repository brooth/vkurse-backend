/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.payment.domain.Payment;

/**
 * A payment. While it is {@code PENDING}, {@code confirmationUrl} is the provider's page where the payer pays.
 */
public record PaymentResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        UUID merchantId,
        UUID schoolId,
        UUID payerId,
        MoneyResponse amount,
        String description,
        String purpose,
        UUID referenceId,
        Payment.Status status,
        @Nullable String confirmationUrl,
        @Nullable Instant paidAt,
        @Nullable Instant canceledAt,
        @Nullable String cancellationReason,
        MoneyResponse refundedAmount) {

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getMerchantId(),
                payment.getSchoolId(),
                payment.getPayerId(),
                MoneyResponse.of(payment.getAmount()),
                payment.getDescription(),
                payment.getPurpose(),
                payment.getReferenceId(),
                payment.getStatus(),
                payment.getConfirmationUrl(),
                payment.getPaidAt(),
                payment.getCanceledAt(),
                payment.getCancellationReason(),
                MoneyResponse.of(payment.getRefundedAmount()));
    }
}
