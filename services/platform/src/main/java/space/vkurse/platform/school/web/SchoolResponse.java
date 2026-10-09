/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import space.vkurse.platform.school.domain.Membership;
import space.vkurse.platform.school.domain.SchoolRole;

/** A school as its staff see it, with the caller's own role in it. */
public record SchoolResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        String name,
        String slug,
        @Nullable String description,
        @Nullable String logo,
        UUID memberId,
        SchoolRole role) {

    public static SchoolResponse of(Membership membership) {
        var school = membership.school();
        return new SchoolResponse(
                school.getId(),
                school.getCreatedAt(),
                school.getUpdatedAt(),
                school.getName(),
                school.getSlug().value(),
                school.getDescription(),
                school.getLogo(),
                membership.member().getId(),
                membership.member().getRole());
    }
}
