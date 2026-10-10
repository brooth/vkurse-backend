/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.DomainEvent;

/**
 * An order was paid: the buyer has the course. Raised once per order.
 *
 * @param accessDays for how many days the buyer has the course; {@code null} for no end
 */
public record OrderPaid(
        UUID orderId,
        UUID schoolId,
        UUID buyerId,
        UUID courseId,
        UUID offerId,
        @Nullable Integer accessDays) implements DomainEvent {}
