/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.learning.application.HomeworkService;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.school.web.FileResponse;
import vkurse.platform.school.web.PersonResponse;

/**
 * An answer to a lesson's homework.
 *
 * @param student who handed it in; only for the staff
 * @param files the attached files; only when one answer is read
 */
public record SubmissionResponse(
        UUID id,
        Instant createdAt,
        UUID schoolId,
        UUID courseId,
        UUID lessonId,
        @Nullable String lessonTitle,
        UUID studentId,
        @Nullable PersonResponse student,
        String text,
        List<UUID> fileIds,
        @Nullable List<FileResponse> files,
        Submission.Status status,
        @Nullable UUID reviewerId,
        @Nullable String comment,
        @Nullable Instant reviewedAt) {

    public static SubmissionResponse of(Submission submission) {
        return of(submission, null, null, null);
    }

    public static SubmissionResponse of(HomeworkService.SubmissionView view, boolean withFiles) {
        return of(
                view.submission(),
                view.lessonTitle(),
                view.student() == null ? null : PersonResponse.of(view.student()),
                withFiles ? view.files().stream().map(FileResponse::of).toList() : null);
    }

    private static SubmissionResponse of(
            Submission submission,
            @Nullable String lessonTitle,
            @Nullable PersonResponse student,
            @Nullable List<FileResponse> files) {
        return new SubmissionResponse(
                submission.getId(),
                submission.getCreatedAt(),
                submission.getSchoolId(),
                submission.getCourseId(),
                submission.getLessonId(),
                lessonTitle,
                submission.getStudentId(),
                student,
                submission.getText(),
                submission.fileIds(),
                files,
                submission.getStatus(),
                submission.getReviewerId(),
                submission.getComment(),
                submission.getReviewedAt());
    }
}
