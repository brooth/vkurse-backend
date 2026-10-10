/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.learning.domain.Curriculum;

/**
 * Whether a lesson is open for the student: {@code OPEN}, {@code COMPLETED}, or {@code LOCKED} with what holds it:
 * {@code opensAt} (its time hasn't come) or {@code blockedBy} (the stop lesson to complete first).
 */
public record LessonStateResponse(
        Curriculum.LessonState.Kind kind,
        @Nullable Instant opensAt,
        @Nullable UUID blockedBy) {

    public static LessonStateResponse of(Curriculum.LessonState state) {
        return new LessonStateResponse(state.kind(), state.opensAt(), state.blockedBy());
    }
}
