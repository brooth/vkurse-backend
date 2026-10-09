/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import java.time.Instant;
import java.util.Optional;

public interface PendingOAuthRequestRepository {
    Optional<PendingOAuthRequest> findByState(String state);

    PendingOAuthRequest save(PendingOAuthRequest request);

    void remove(PendingOAuthRequest request);

    /** Removes sign-ins that were never finished and expired before {@code now}. Returns how many. */
    int deleteExpired(Instant now);
}
