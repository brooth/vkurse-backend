/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface LinkedAccountRepository {
    Optional<LinkedAccount> findBySubject(SignInProvider provider, String subject);

    LinkedAccount save(LinkedAccount account);

    /** Removes every linked account of a user. Returns how many. */
    int deleteByUser(UUID userId);
}
