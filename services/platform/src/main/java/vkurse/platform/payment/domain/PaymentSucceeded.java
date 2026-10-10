/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/**
 * A payment was paid. Raised once per payment.
 *
 * @param purpose what the payment was started for, as its starter named it
 * @param referenceId the starter's own id for what was paid
 */
public record PaymentSucceeded(UUID paymentId, String purpose, UUID referenceId) implements DomainEvent {}
