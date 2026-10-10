/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.EnrollmentRepository;
import vkurse.platform.learning.domain.LearningErrors.HomeworkNotExpectedException;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.learning.domain.SubmissionRepository;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.Upload;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/**
 * Homework: a student hands in answers to a lesson's homework, and the staff of the school review them. Storage is
 * never called inside a transaction here.
 */
@Service
public class HomeworkService {
    private static final Set<FilePurpose> HOMEWORK = Set.of(FilePurpose.HOMEWORK_FILE);

    private final LearningAccess access;
    private final Progress progress;
    private final SubmissionRepository submissions;
    private final EnrollmentRepository enrollments;
    private final StoredFileService files;
    private final CourseReader reader;
    private final UserService users;
    private final LearningNotifier notifier;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public HomeworkService(
            LearningAccess access,
            Progress progress,
            SubmissionRepository submissions,
            EnrollmentRepository enrollments,
            StoredFileService files,
            CourseReader reader,
            UserService users,
            LearningNotifier notifier,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.progress = progress;
        this.submissions = submissions;
        this.enrollments = enrollments;
        this.files = files;
        this.reader = reader;
        this.users = users;
        this.notifier = notifier;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Step 1 of attaching a file to an answer: asks for a link to upload it with. */
    public Upload requestUpload(UUID userId, UUID lessonId, String name, String contentType, long size) {
        var schoolId = homeworkSchool(userId, lessonId);
        return files.requestUpload(FilePurpose.HOMEWORK_FILE, schoolId, userId, name, contentType, size);
    }

    /** Step 2, after the file was sent to storage: makes it usable in an answer. Only the uploader may. */
    public StoredFile confirmUpload(UUID userId, UUID lessonId, UUID fileId) {
        var schoolId = homeworkSchool(userId, lessonId);
        return files.confirm(fileId, userId, FilePurpose.HOMEWORK_FILE, schoolId);
    }

    /**
     * Hands in the caller's answer to the homework of a lesson that is open for them.
     *
     * @param fileIds the caller's own confirmed uploads to attach
     */
    public Submission handIn(UUID userId, UUID lessonId, @Nullable String text, List<UUID> fileIds) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var studying = openHomework(userId, lessonId, true);
            var enrollment = studying.enrollment();
            var attached = files.getAll(enrollment.getSchoolId(), HOMEWORK, Set.copyOf(fileIds));
            var latest = submissions.findLatest(enrollment.getId(), lessonId).orElse(null);
            return submissions.save(
                    Submission.handIn(enrollment, lessonId, text, attached.values(), latest, clock.instant()));
        }));
    }

    /** The caller's answers to a lesson's homework, the latest first. */
    public List<Submission> history(UUID userId, UUID lessonId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var studying = access.lesson(userId, lessonId, false);
            return submissions.findHistory(studying.enrollment().getId(), lessonId);
        }));
    }

    /**
     * A school's answers, those that waited longest first. For the staff.
     *
     * @param status only answers in that status; {@code null} for any
     * @param courseId only answers of that course; {@code null} for any
     */
    public List<SubmissionView> queue(
            UUID userId, UUID schoolId, Submission.@Nullable Status status, @Nullable UUID courseId, Paging paging) {
        return Objects.requireNonNull(transaction.execute(tx -> {
            access.staff(userId, schoolId, "School");
            return submissions.findBySchool(schoolId, status, courseId, paging.limit(), paging.offset()).stream()
                    .map(submission -> view(submission, List.of()))
                    .toList();
        }));
    }

    /** An answer with its files, for the student who handed it in and for the staff of its school. */
    public SubmissionView get(UUID userId, UUID submissionId) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var submission = viewable(userId, submissionId);
            return view(submission, attachments(submission));
        }));
    }

    /** Signed links to an answer's files, by file id, for the same people as {@link #get}. */
    public Map<UUID, Download> links(UUID userId, UUID submissionId) {
        var attached =
                Objects.requireNonNull(transaction.execute(status -> attachments(viewable(userId, submissionId))));
        return files.links(attached);
    }

    /** The staff accept an answer. That completes the lesson, if nothing else of it is left to do. */
    public SubmissionView accept(UUID userId, UUID submissionId, @Nullable String comment) {
        return review(userId, submissionId, submission -> submission.accept(userId, comment, clock.instant()));
    }

    /** The staff give an answer back, saying what to change. */
    public SubmissionView giveBack(UUID userId, UUID submissionId, String comment) {
        return review(userId, submissionId, submission -> submission.giveBack(userId, comment, clock.instant()));
    }

    private SubmissionView review(UUID userId, UUID submissionId, Consumer<Submission> verdict) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var submission = submissions.findByIdForUpdate(submissionId).orElseThrow(HomeworkService::notFound);
            access.staff(userId, submission.getSchoolId(), "Submission");
            // Locked, so that the lesson is completed once whatever else the student does at this moment.
            var enrollment =
                    enrollments.findByIdForUpdate(submission.getEnrollmentId()).orElseThrow();
            verdict.accept(submission);
            submissions.save(submission);
            reader.lesson(submission.getLessonId())
                    .ifPresent(lesson -> progress.completeIfWorkDone(enrollment, lesson));
            notifier.homeworkReviewed(submission);
            return view(submission, attachments(submission));
        }));
    }

    /** The school of a lesson whose homework the caller may answer now. */
    private UUID homeworkSchool(UUID userId, UUID lessonId) {
        return Objects.requireNonNull(transaction.execute(
                status -> openHomework(userId, lessonId, false).enrollment().getSchoolId()));
    }

    private StudyingLesson openHomework(UUID userId, UUID lessonId, boolean lock) {
        var studying = access.lesson(userId, lessonId, lock);
        studying.ensureOpen();
        if (studying.lesson().getContent().homework().isEmpty()) {
            throw new HomeworkNotExpectedException("This lesson has no homework");
        }
        return studying;
    }

    private Submission viewable(UUID userId, UUID submissionId) {
        var submission = submissions.findById(submissionId).orElseThrow(HomeworkService::notFound);
        if (!submission.isOf(userId)) {
            access.staff(userId, submission.getSchoolId(), "Submission");
        }
        return submission;
    }

    /** The answer's files that storage still has. */
    private List<StoredFile> attachments(Submission submission) {
        var attached = new ArrayList<StoredFile>();
        for (var fileId : submission.fileIds()) {
            try {
                attached.add(files.get(submission.getSchoolId(), HOMEWORK, fileId));
            } catch (NotFoundException gone) {
                // Shown without it.
            }
        }
        return attached;
    }

    private SubmissionView view(Submission submission, List<StoredFile> attached) {
        return new SubmissionView(
                submission,
                users.findById(submission.getStudentId()).orElse(null),
                reader.lesson(submission.getLessonId()).map(Lesson::getTitle).orElse(null),
                attached);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Submission not found");
    }

    /**
     * An answer with what the staff need around it.
     *
     * @param student {@code null} if the account is gone
     * @param lessonTitle {@code null} if the lesson was removed
     * @param files the answer's files; empty in a list
     */
    public record SubmissionView(
            Submission submission,
            @Nullable User student,
            @Nullable String lessonTitle,
            List<StoredFile> files) {}
}
