/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * How a student is given a course: by a paid order, or by the staff.
 *
 * @param accessDays for how many days; {@code null} for no end
 */
public record AccessGrant(
        Enrollment.Source source,
        @Nullable UUID orderId,
        @Nullable UUID grantedBy,
        @Nullable Integer accessDays) {
    public static final int MAX_DAYS = 3650;

    public AccessGrant {
        if (accessDays != null && (accessDays < 1 || accessDays > MAX_DAYS)) {
            throw new BadRequestException("Access lasts 1 to " + MAX_DAYS + " days");
        }
    }

    public static AccessGrant byOrder(UUID orderId, @Nullable Integer accessDays) {
        return new AccessGrant(Enrollment.Source.ORDER, orderId, null, accessDays);
    }

    public static AccessGrant byStaff(UUID grantedBy, @Nullable Integer accessDays) {
        return new AccessGrant(Enrollment.Source.GRANT, null, grantedBy, accessDays);
    }
}
