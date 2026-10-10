/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.domain;

import java.util.UUID;
import vkurse.core.domain.DomainEvent;

/** The whole payment of a paid order went back to the buyer: they no longer have the course by this order. */
public record OrderRefunded(UUID orderId, UUID schoolId, UUID buyerId, UUID courseId) implements DomainEvent {}
