/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.Curriculum;
import vkurse.platform.learning.domain.LessonWork;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.learning.domain.SubmissionRepository;
import vkurse.platform.storage.domain.Download;

/** A student reads the courses they have. */
@Service
public class StudyService {
    private final LearningAccess access;
    private final Progress progress;
    private final SubmissionRepository submissions;
    private final CourseReader reader;
    private final TransactionTemplate transaction;

    public StudyService(
            LearningAccess access,
            Progress progress,
            SubmissionRepository submissions,
            CourseReader reader,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.progress = progress;
        this.submissions = submissions;
        this.reader = reader;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** A course the caller has: its sections, its published lessons, and whether each lesson is open for them. */
    @Transactional(readOnly = true)
    public Studying outline(UUID userId, UUID courseId) {
        return access.course(userId, courseId, false);
    }

    /** A lesson of a course the caller has, if it is open for them. */
    @Transactional(readOnly = true)
    public LessonView lesson(UUID userId, UUID lessonId) {
        return view(access.lesson(userId, lessonId, false));
    }

    /**
     * Signed links to the files the lesson's blocks show, by block id, if the lesson is open for the caller. They are
     * signed outside a transaction.
     */
    public Map<UUID, Download> links(UUID userId, UUID lessonId) {
        var studying = Objects.requireNonNull(transaction.execute(status -> {
            var found = access.lesson(userId, lessonId, false);
            found.ensureOpen();
            return found;
        }));
        return reader.links(studying.enrollment().getSchoolId(), studying.lesson());
    }

    /**
     * The caller marks a lesson completed. A lesson with homework or a quiz is completed by that work, so marking it
     * before the work is done is refused. Completing a completed lesson changes nothing.
     */
    @Transactional
    public LessonView complete(UUID userId, UUID lessonId) {
        var studying = access.lesson(userId, lessonId, true);
        studying.ensureOpen();
        progress.workOf(studying.enrollment(), studying.lesson()).ensureDone();
        progress.complete(studying.enrollment(), studying.lesson());
        return view(access.lesson(userId, lessonId, false));
    }

    private LessonView view(StudyingLesson studying) {
        var state = studying.ensureOpen();
        var enrollment = studying.enrollment();
        var lesson = studying.lesson();
        return new LessonView(
                lesson,
                state,
                progress.workOf(enrollment, lesson),
                submissions.findLatest(enrollment.getId(), lesson.getId()).orElse(null));
    }

    /**
     * A lesson as its student sees it.
     *
     * @param homework the student's last answer to the lesson's homework
     */
    public record LessonView(
            Lesson lesson,
            Curriculum.LessonState state,
            LessonWork work,
            @Nullable Submission homework) {}
}
