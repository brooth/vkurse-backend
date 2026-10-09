/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.domain;

import java.util.UUID;
import space.vkurse.core.domain.DomainEvent;

/** A user's account was deleted. Published in the transaction that deleted it. */
public record UserDeleted(UUID userId) implements DomainEvent {}
