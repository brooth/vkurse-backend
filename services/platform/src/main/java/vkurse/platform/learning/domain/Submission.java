/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.learning.domain.LearningErrors.AlreadyReviewedException;
import vkurse.platform.learning.domain.LearningErrors.HomeworkNotExpectedException;
import vkurse.platform.storage.domain.StoredFile;

/**
 * One answer a student handed in to a lesson's homework. An answer that was given back is followed by a new one, so
 * the rows of a student and lesson are the history of their answers and the staff's comments.
 */
@Entity
@Table(name = "homework_submissions")
@Getter
public class Submission extends AggregateRoot {
    public static final int MAX_TEXT_LENGTH = 20_000;
    public static final int MAX_FILES = 10;
    public static final int MAX_COMMENT_LENGTH = 5000;

    private UUID enrollmentId;

    private UUID schoolId;

    private UUID courseId;

    private UUID lessonId;

    private UUID studentId;

    /** The answer, in Markdown; empty when only files were handed in. */
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    private SubmissionFiles files;

    @Enumerated(EnumType.STRING)
    private Status status = Status.SUBMITTED;

    private @Nullable UUID reviewerId;

    /** What the reviewer said. */
    private @Nullable String comment;

    private @Nullable Instant reviewedAt;

    protected Submission() {}

    private Submission(Enrollment enrollment, UUID lessonId, String text, SubmissionFiles files, Instant now) {
        super(now);
        this.enrollmentId = enrollment.getId();
        this.schoolId = enrollment.getSchoolId();
        this.courseId = enrollment.getCourseId();
        this.lessonId = lessonId;
        this.studentId = enrollment.getStudentId();
        this.text = text;
        this.files = files;
    }

    /**
     * Hands in an answer: text, files, or both. A student answers when they haven't yet, or when their last answer
     * was given back.
     *
     * @param files the stored files to hand in; each must be the student's own upload
     * @param latest the student's last answer to this lesson's homework
     */
    public static Submission handIn(
            Enrollment enrollment,
            UUID lessonId,
            @Nullable String text,
            Collection<StoredFile> files,
            @Nullable Submission latest,
            Instant now) {
        if (latest != null && latest.status != Status.RETURNED) {
            throw new HomeworkNotExpectedException(
                    latest.status == Status.ACCEPTED
                            ? "Your homework is accepted already"
                            : "Your answer is waiting for review");
        }
        var answer = text == null ? "" : text.strip();
        if (answer.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("An answer has at most " + MAX_TEXT_LENGTH + " characters");
        }
        if (files.size() > MAX_FILES) {
            throw new BadRequestException("An answer has at most " + MAX_FILES + " files");
        }
        if (answer.isEmpty() && files.isEmpty()) {
            throw new BadRequestException("Write an answer or attach a file");
        }
        for (var file : files) {
            if (!file.getUploadedBy().equals(enrollment.getStudentId())) {
                throw new NotFoundException("File not found");
            }
        }
        var fileIds = files.stream().map(StoredFile::getId).distinct().toList();
        return new Submission(enrollment, lessonId, answer, new SubmissionFiles(fileIds), now);
    }

    public boolean isOf(UUID studentId) {
        return this.studentId.equals(studentId);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    public List<UUID> fileIds() {
        return files.fileIds();
    }

    /** The staff accept the answer: the homework is done. */
    public void accept(UUID reviewerId, @Nullable String comment, Instant now) {
        review(Status.ACCEPTED, reviewerId, comment == null || comment.isBlank() ? null : comment.strip(), now);
    }

    /** The staff give the answer back, saying what to change; the student hands in another. */
    public void giveBack(UUID reviewerId, String comment, Instant now) {
        if (comment.isBlank()) {
            throw new BadRequestException("Say what to change");
        }
        review(Status.RETURNED, reviewerId, comment.strip(), now);
    }

    private void review(Status outcome, UUID reviewerId, @Nullable String comment, Instant now) {
        if (status != Status.SUBMITTED) {
            throw new AlreadyReviewedException();
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException("A comment has at most " + MAX_COMMENT_LENGTH + " characters");
        }
        this.status = outcome;
        this.reviewerId = reviewerId;
        this.comment = comment;
        this.reviewedAt = now;
        raise(new HomeworkReviewed(getId(), lessonId, studentId, outcome == Status.ACCEPTED));
    }

    public enum Status {
        /** Waits for review. */
        SUBMITTED,
        /** Accepted: the homework is done. */
        ACCEPTED,
        /** Given back to the student to change. */
        RETURNED
    }
}
