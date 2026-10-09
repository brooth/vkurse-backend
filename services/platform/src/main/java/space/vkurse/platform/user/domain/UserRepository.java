/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.domain;

import java.util.Optional;
import java.util.UUID;

/** Where users are kept. Deleted users are never returned. */
public interface UserRepository {
    Optional<User> findById(UUID id);

    Optional<User> findByEmail(Email email);

    /** Writes a new or changed user and publishes the events it raised. */
    User save(User user);
}
