/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import space.vkurse.platform.user.domain.User;

/** A user as the staff of a school see the people in it. */
public record PersonResponse(
        UUID id, String name, String email, @Nullable String picture) {

    public static PersonResponse of(User user) {
        return new PersonResponse(user.getId(), user.getName(), user.getEmail().value(), user.getPicture());
    }
}
