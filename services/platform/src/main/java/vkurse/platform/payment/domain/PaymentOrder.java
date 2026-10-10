/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.money.Money;

/**
 * What we ask a provider to charge.
 *
 * @param paymentId our payment's id. It is also the idempotence key: asking twice creates one payment
 * @param returnUrl where the provider sends the payer back to
 * @param receipt what to put on the fiscal receipt; {@code null} if the merchant issues none through the provider
 */
public record PaymentOrder(
        UUID paymentId,
        Money amount,
        String description,
        URI returnUrl,
        @Nullable Receipt receipt) {

    /**
     * A fiscal receipt of one line: the description of the payment, for its whole amount.
     *
     * @param email where the payer gets the receipt
     * @param vatCode the provider's code of the VAT rate
     */
    public record Receipt(String email, int vatCode) {}
}
