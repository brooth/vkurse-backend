/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import vkurse.core.money.Money;

/**
 * A payment as the provider has it: the only source of truth about whether money was paid.
 *
 * @param id the provider's id of the payment
 * @param confirmationUrl the provider's page where the payer pays; only while the payment waits for that
 * @param cancellationReason the provider's word for why it was canceled
 */
public record ProviderPayment(
        String id,
        State state,
        Money amount,
        @Nullable URI confirmationUrl,
        @Nullable String cancellationReason) {

    public enum State {
        /** Not paid yet. */
        PENDING,
        /** The money is the merchant's. */
        SUCCEEDED,
        /** Will never be paid. */
        CANCELED
    }
}
