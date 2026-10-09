/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository {
    Optional<Session> findById(UUID id);

    /** Loads the session and locks its row until the transaction ends, so concurrent refreshes wait for each other. */
    Optional<Session> findByIdForUpdate(UUID id);

    /** The session a one-time exchange code belongs to, locked like {@link #findByIdForUpdate}. */
    Optional<Session> findByExchangeCodeHashForUpdate(String codeHash);

    /** A user's sessions that are live at {@code now}, newest first. */
    List<Session> findLiveByUser(UUID userId, Instant now, int limit, int offset);

    /** Every session of a user that is live at {@code now}. */
    List<Session> findLiveByUser(UUID userId, Instant now);

    Session save(Session session);

    /** Removes sessions that expired before {@code now}, revoked or not. Returns how many. */
    int deleteExpired(Instant now);
}
