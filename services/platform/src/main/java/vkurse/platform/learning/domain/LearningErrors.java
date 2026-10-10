/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

public final class LearningErrors {

    private LearningErrors() {}

    /**
     * 403 {@code learning/lesson-locked}: the student has the course, but this lesson isn't open for them yet. The
     * body says why: {@code opensAt}, or {@code blockedBy} (the stop lesson to complete first).
     */
    public static class LessonLockedException extends ClientException {
        public LessonLockedException(@Nullable Instant opensAt, @Nullable UUID blockedBy) {
            super(
                    HttpStatus.FORBIDDEN,
                    "learning/lesson-locked",
                    "This lesson isn't open yet",
                    why(opensAt, blockedBy));
        }

        private static HashMap<String, Object> why(@Nullable Instant opensAt, @Nullable UUID blockedBy) {
            var why = new HashMap<String, Object>();
            if (opensAt != null) {
                why.put("opensAt", opensAt.toString());
            }
            if (blockedBy != null) {
                why.put("blockedBy", blockedBy.toString());
            }
            return why;
        }
    }

    /** 409 {@code learning/work-pending}: the lesson's homework isn't accepted, or its quiz isn't passed. */
    public static class WorkPendingException extends ClientException {
        public WorkPendingException() {
            super(
                    HttpStatus.CONFLICT,
                    "learning/work-pending",
                    "The lesson is completed when its homework is accepted and its quiz is passed");
        }
    }

    /**
     * 409 {@code learning/homework-not-expected}: the lesson has no homework, or the student's answer is waiting for
     * review or is accepted already.
     */
    public static class HomeworkNotExpectedException extends ClientException {
        public HomeworkNotExpectedException(String message) {
            super(HttpStatus.CONFLICT, "learning/homework-not-expected", message);
        }
    }

    /** 409 {@code learning/already-reviewed}: the answer was accepted or given back already. */
    public static class AlreadyReviewedException extends ClientException {
        public AlreadyReviewedException() {
            super(HttpStatus.CONFLICT, "learning/already-reviewed", "This answer was reviewed already");
        }
    }
}
