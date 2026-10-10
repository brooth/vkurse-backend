/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/** A payment will never be paid. Raised once per payment. */
public record PaymentCanceled(UUID paymentId, String purpose, UUID referenceId) implements DomainEvent {}
