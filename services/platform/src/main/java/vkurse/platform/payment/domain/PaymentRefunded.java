/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;
import vkurse.core.money.Money;

/**
 * Money of a payment went back to the payer. Raised once per refund.
 *
 * @param amount what this refund gave back
 * @param fully whether nothing of the payment is left with the merchant
 */
public record PaymentRefunded(
        UUID paymentId, String purpose, UUID referenceId, UUID refundId, Money amount, boolean fully)
        implements DomainEvent {}
