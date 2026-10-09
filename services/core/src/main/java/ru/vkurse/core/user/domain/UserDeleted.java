/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.user.domain;

import java.util.UUID;
import ru.vkurse.common.domain.DomainEvent;

/** A user's account was deleted. Published in the transaction that deleted it. */
public record UserDeleted(UUID userId) implements DomainEvent {}
