/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import space.vkurse.platform.user.domain.Email;

public interface OtpChallengeRepository {
    /** Loads the challenge and locks its row, so concurrent attempts wait and each one sees the attempts left. */
    Optional<OtpChallenge> findByIdForUpdate(UUID id);

    /** The challenge that was issued for this email last, if any is left. */
    Optional<OtpChallenge> findLatestByEmail(Email email);

    OtpChallenge save(OtpChallenge challenge);

    void remove(OtpChallenge challenge);

    /** Removes a challenge by id, if it is still there. */
    void removeById(UUID id);

    /** Removes challenges that expired before {@code now}. Returns how many. */
    int deleteExpired(Instant now);
}
