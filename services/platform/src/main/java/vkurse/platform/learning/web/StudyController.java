/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.course.domain.LessonBlock;
import vkurse.platform.course.web.LinkResponse;
import vkurse.platform.learning.application.EnrollmentService;
import vkurse.platform.learning.application.QuizService;
import vkurse.platform.learning.application.StudyService;
import vkurse.platform.learning.domain.QuizAttempt;

/** A student's own courses and lessons. Everything here is about the caller. */
@RestController
@RequestMapping("/learning")
class StudyController {
    private final EnrollmentService enrollments;
    private final StudyService study;
    private final QuizService quizzes;

    StudyController(EnrollmentService enrollments, StudyService study, QuizService quizzes) {
        this.enrollments = enrollments;
        this.study = study;
        this.quizzes = quizzes;
    }

    /** The courses the caller has now, the latest first. */
    @GetMapping("/courses")
    List<MyCourseResponse> courses(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return enrollments.listMine(session.userId(), Paging.parse(params)).stream()
                .map(MyCourseResponse::of)
                .toList();
    }

    /** A course the caller has: its sections and lessons, and whether each lesson is open for them. */
    @GetMapping("/courses/{courseId}")
    StudyOutlineResponse course(@GetSession UserSession session, @PathVariable UUID courseId) {
        return StudyOutlineResponse.of(study.outline(session.userId(), courseId));
    }

    /**
     * A lesson of a course the caller has. A lesson that isn't open yet is 403 {@code learning/lesson-locked}, with
     * {@code opensAt} or {@code blockedBy} (the stop lesson to complete first).
     */
    @GetMapping("/lessons/{id}")
    StudyLessonResponse lesson(@GetSession UserSession session, @PathVariable UUID id) {
        return StudyLessonResponse.of(study.lesson(session.userId(), id));
    }

    /** Signed links to the files the lesson's blocks show, by block id. */
    @PostMapping("/lessons/{id}/links")
    Map<UUID, LinkResponse> links(@GetSession UserSession session, @PathVariable UUID id) {
        return LinkResponse.of(study.links(session.userId(), id));
    }

    /**
     * Marks the lesson completed. A lesson with homework or a quiz is completed by that work: until it is done, this
     * is 409 {@code learning/work-pending}.
     */
    @PostMapping("/lessons/{id}/complete")
    StudyLessonResponse complete(@GetSession UserSession session, @PathVariable UUID id) {
        return StudyLessonResponse.of(study.complete(session.userId(), id));
    }

    /** Answers the lesson's quiz: {@code answers} is the chosen option ids by question id. Graded at once. */
    @PostMapping("/lessons/{id}/quiz")
    AttemptResponse answer(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody AnswerRequest data) {
        return AttemptResponse.of(quizzes.answer(session.userId(), id, data.answers()));
    }

    /** The caller's attempts at the lesson's quiz, the latest first. */
    @GetMapping("/lessons/{id}/quiz")
    List<AttemptResponse> attempts(@GetSession UserSession session, @PathVariable UUID id) {
        return quizzes.attempts(session.userId(), id).stream()
                .map(AttemptResponse::of)
                .toList();
    }

    record AnswerRequest(
            @NotNull @Size(max = LessonBlock.Quiz.MAX_QUESTIONS)
            Map<UUID, List<UUID>> answers) {}

    /** {@code wrongQuestionIds} names the questions answered wrong; which options are right is never said. */
    record AttemptResponse(
            UUID id,
            Instant createdAt,
            UUID lessonId,
            int right,
            int total,
            boolean passed,
            Map<UUID, List<UUID>> answers,
            List<UUID> wrongQuestionIds) {
        static AttemptResponse of(QuizAttempt attempt) {
            return new AttemptResponse(
                    attempt.getId(),
                    attempt.getCreatedAt(),
                    attempt.getLessonId(),
                    attempt.getRightCount(),
                    attempt.getQuestionCount(),
                    attempt.isPassed(),
                    attempt.getAnswers().chosen(),
                    attempt.getAnswers().wrongQuestionIds());
        }
    }

    record MyCourseResponse(
            UUID enrollmentId,
            UUID courseId,
            UUID schoolId,
            String title,
            @Nullable String summary,
            @Nullable String cover,
            Instant startedAt,
            @Nullable Instant expiresAt,
            long lessons,
            long completed) {
        static MyCourseResponse of(EnrollmentService.MyCourse mine) {
            return new MyCourseResponse(
                    mine.enrollment().getId(),
                    mine.course().getId(),
                    mine.course().getSchoolId(),
                    mine.course().getTitle(),
                    mine.course().getSummary(),
                    mine.course().getCover(),
                    mine.enrollment().getStartedAt(),
                    mine.enrollment().getExpiresAt(),
                    mine.lessons(),
                    mine.completed());
        }
    }
}
