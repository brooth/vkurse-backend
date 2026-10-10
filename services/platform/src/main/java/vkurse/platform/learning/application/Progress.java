/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import org.springframework.stereotype.Component;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.learning.domain.LessonCompletion;
import vkurse.platform.learning.domain.LessonCompletionRepository;
import vkurse.platform.learning.domain.LessonWork;
import vkurse.platform.learning.domain.QuizAttemptRepository;
import vkurse.platform.learning.domain.SubmissionRepository;

/** A student's work on lessons, and the completion it leads to. Call it with the enrollment locked. */
@Component
class Progress {
    private final LessonCompletionRepository completions;
    private final SubmissionRepository submissions;
    private final QuizAttemptRepository attempts;
    private final Clock clock;

    Progress(
            LessonCompletionRepository completions,
            SubmissionRepository submissions,
            QuizAttemptRepository attempts,
            Clock clock) {
        this.completions = completions;
        this.submissions = submissions;
        this.attempts = attempts;
        this.clock = clock;
    }

    /** What the lesson asks of the student, and how far they are with it. */
    LessonWork workOf(Enrollment enrollment, Lesson lesson) {
        return LessonWork.of(
                lesson,
                submissions.findLatest(enrollment.getId(), lesson.getId()).orElse(null),
                attempts.hasPassed(enrollment.getId(), lesson.getId()));
    }

    /** Records that the student completed the lesson, once. */
    void complete(Enrollment enrollment, Lesson lesson) {
        if (!completions.exists(enrollment.getId(), lesson.getId())) {
            completions.save(LessonCompletion.record(enrollment, lesson.getId(), clock.instant()));
        }
    }

    /** Completes the lesson if its homework and quiz are all it takes, and both are done. */
    void completeIfWorkDone(Enrollment enrollment, Lesson lesson) {
        if (workOf(enrollment, lesson).completesLesson()) {
            complete(enrollment, lesson);
        }
    }
}
