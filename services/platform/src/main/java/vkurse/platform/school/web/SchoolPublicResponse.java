/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.school.domain.School;

/** A school as anyone may see it. */
public record SchoolPublicResponse(
        UUID id,
        String name,
        String slug,
        @Nullable String description,
        @Nullable String logo) {

    public static SchoolPublicResponse of(School school) {
        return new SchoolPublicResponse(
                school.getId(), school.getName(), school.getSlug().value(), school.getDescription(), school.getLogo());
    }
}
