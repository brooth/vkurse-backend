/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import space.vkurse.platform.user.domain.User;

/** A user as the API shows it. */
public record UserResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        User.Role role,
        String name,
        String email,
        @Nullable String picture,
        String locale,
        String timezone) {

    public static UserResponse of(User user) {
        return new UserResponse(
                user.getId(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getRole(),
                user.getName(),
                user.getEmail().value(),
                user.getPicture(),
                user.getLocale(),
                user.getTimezone());
    }
}
