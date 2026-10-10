/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.money.Money;

/**
 * What we ask a provider to give back.
 *
 * @param refundId our refund's id. It is also the idempotence key: asking twice refunds once
 */
public record RefundOrder(
        UUID refundId,
        String providerPaymentId,
        Money amount,
        @Nullable String reason) {}
