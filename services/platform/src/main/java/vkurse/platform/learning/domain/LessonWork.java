/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.LearningErrors.WorkPendingException;

/**
 * What a lesson asks of a student, and how far they are with it. A lesson with homework is done when the homework is
 * accepted; one with a quiz, when the quiz is passed; one with both needs both. A lesson with neither asks nothing.
 *
 * @param homework the status of the student's last answer; {@code null} if they handed in none
 */
public record LessonWork(
        boolean hasHomework, Submission.@Nullable Status homework, boolean hasQuiz, boolean quizPassed) {

    public static LessonWork of(Lesson lesson, @Nullable Submission latest, boolean quizPassed) {
        var content = lesson.getContent();
        return new LessonWork(
                content.homework().isPresent(),
                latest == null ? null : latest.getStatus(),
                content.quiz().isPresent(),
                quizPassed);
    }

    /** Whether nothing of the lesson's work is left to do. */
    public boolean isDone() {
        return (!hasHomework || homework == Submission.Status.ACCEPTED) && (!hasQuiz || quizPassed);
    }

    /** Whether the lesson is completed by its work alone, with no word from the student. */
    public boolean completesLesson() {
        return (hasHomework || hasQuiz) && isDone();
    }

    /** Checks that the student may mark the lesson completed. */
    public void ensureDone() {
        if (!isDone()) {
            throw new WorkPendingException();
        }
    }
}
