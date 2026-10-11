/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.ForbiddenException;
import vkurse.platform.school.domain.SchoolRole;

/**
 * Someone in a chat: a member of the school's staff, or a student who has the course now.
 *
 * @param role the role on the school's staff; {@code null} for a student
 */
public record ChatMember(UUID userId, @Nullable SchoolRole role) {

    public boolean isStaff() {
        return role != null;
    }

    /** Banners, pins and deleting other people's messages are the staff's. */
    public void ensureStaff() {
        if (!isStaff()) {
            throw new ForbiddenException("Only the school's staff may do this");
        }
    }
}
