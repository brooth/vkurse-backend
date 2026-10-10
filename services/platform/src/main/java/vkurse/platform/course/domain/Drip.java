/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import vkurse.core.error.Errors.BadRequestException;

/**
 * When a lesson opens for a student who has the course: at once, some days after they got the course, or at a set
 * time for everyone.
 *
 * @param days for {@link Kind#AFTER_DAYS}: days since the student got the course
 * @param at for {@link Kind#AT_TIME}: the moment the lesson opens
 */
@Embeddable
public record Drip(
        @Enumerated(EnumType.STRING) Kind kind,
        @Nullable Integer days,
        @Nullable Instant at) {
    public static final int MAX_DAYS = 3650;

    public static final Drip IMMEDIATELY = new Drip(Kind.IMMEDIATELY, null, null);

    /** Makes a rule from what a client sent, keeping only the value its kind uses. */
    public static Drip of(Kind kind, @Nullable Integer days, @Nullable Instant at) {
        return switch (kind) {
            case IMMEDIATELY -> IMMEDIATELY;
            case AFTER_DAYS -> {
                if (days == null || days < 1 || days > MAX_DAYS) {
                    throw new BadRequestException("A lesson opens after 1 to " + MAX_DAYS + " days");
                }
                yield new Drip(kind, days, null);
            }
            case AT_TIME -> {
                if (at == null) {
                    throw new BadRequestException("Say when the lesson opens");
                }
                yield new Drip(kind, null, at);
            }
        };
    }

    public enum Kind {
        IMMEDIATELY,
        AFTER_DAYS,
        AT_TIME
    }
}
