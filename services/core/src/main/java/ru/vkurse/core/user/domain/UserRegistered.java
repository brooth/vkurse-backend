/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.user.domain;

import java.util.UUID;
import ru.vkurse.common.domain.DomainEvent;

/** A new user signed up. Published in the transaction that created the user. */
public record UserRegistered(UUID userId) implements DomainEvent {}
