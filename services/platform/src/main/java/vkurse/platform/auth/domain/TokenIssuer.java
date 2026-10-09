/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Signs the tokens of a session, and reads a refresh token back. */
public interface TokenIssuer {

    /** A token other services accept as proof of who the caller is, until {@code expiresAt}. */
    String access(UUID userId, UUID sessionId, String role, Instant expiresAt);

    /** A token that can be traded once for new tokens, as long as {@code generation} is the session's current one. */
    String refresh(UUID userId, UUID sessionId, int generation, Instant expiresAt);

    /** Reads a refresh token. Empty if it isn't ours, isn't a refresh token or has expired. */
    Optional<RefreshClaims> parseRefresh(String refreshToken);

    record RefreshClaims(UUID sessionId, int generation) {}
}
