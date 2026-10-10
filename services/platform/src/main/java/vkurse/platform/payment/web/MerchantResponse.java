/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.web;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.PaymentProvider;

/**
 * A merchant as the school's owner and admins see it. The credentials are never shown. {@code notificationUrl} is
 * what the school sets in its shop with the provider, so the provider tells us about payments.
 */
public record MerchantResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        UUID schoolId,
        PaymentProvider provider,
        String name,
        String accountId,
        boolean testMode,
        Receipts receipts,
        Merchant.Status status,
        URI notificationUrl) {

    public static MerchantResponse of(Merchant merchant, URI publicUrl) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt(),
                merchant.getSchoolId(),
                merchant.getProvider(),
                merchant.getName(),
                merchant.getAccountId(),
                merchant.isTestMode(),
                new Receipts(
                        merchant.getReceipts().enabled(), merchant.getReceipts().vatCode()),
                merchant.getStatus(),
                PaymentWebhookController.notificationUrl(publicUrl, merchant));
    }

    public record Receipts(boolean enabled, @Nullable Integer vatCode) {}
}
