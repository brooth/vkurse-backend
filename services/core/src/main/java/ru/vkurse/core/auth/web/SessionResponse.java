/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.vkurse.core.auth.domain.Session;
import ru.vkurse.core.auth.domain.SignInProvider;

/** A signed-in device as the API shows it. */
record SessionResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        SignInProvider provider,
        @Nullable String userAgent,
        Instant lastActivityAt,
        Instant expiresAt) {

    static SessionResponse of(Session session) {
        return new SessionResponse(
                session.getId(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getProvider(),
                session.getUserAgent(),
                session.getLastActivityAt(),
                session.getExpiresAt());
    }
}
