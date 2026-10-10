/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.LessonBlock;
import vkurse.platform.learning.application.StudyService;
import vkurse.platform.learning.domain.LessonWork;
import vkurse.platform.learning.domain.Submission;

/**
 * A lesson as its student sees it. In a quiz block no option says whether it is right.
 *
 * @param homework the student's last answer to the lesson's homework
 */
public record StudyLessonResponse(
        UUID id,
        UUID courseId,
        UUID sectionId,
        String title,
        boolean stop,
        LessonStateResponse state,
        List<LessonBlock> blocks,
        WorkResponse work,
        @Nullable SubmissionResponse homework) {

    public static StudyLessonResponse of(StudyService.LessonView view) {
        var lesson = view.lesson();
        return new StudyLessonResponse(
                lesson.getId(),
                lesson.getCourseId(),
                lesson.getSectionId(),
                lesson.getTitle(),
                lesson.isStop(),
                LessonStateResponse.of(view.state()),
                lesson.getContent().forStudents().blocks(),
                WorkResponse.of(view.work()),
                view.homework() == null ? null : SubmissionResponse.of(view.homework()));
    }

    /**
     * What the lesson asks of the student. {@code homework} is the status of their last answer, {@code null} if they
     * handed in none. {@code done} says that nothing is left to do, so the lesson can be completed.
     */
    public record WorkResponse(
            boolean hasHomework,
            Submission.@Nullable Status homework,
            boolean hasQuiz,
            boolean quizPassed,
            boolean done) {

        static WorkResponse of(LessonWork work) {
            return new WorkResponse(
                    work.hasHomework(), work.homework(), work.hasQuiz(), work.quizPassed(), work.isDone());
        }
    }
}
