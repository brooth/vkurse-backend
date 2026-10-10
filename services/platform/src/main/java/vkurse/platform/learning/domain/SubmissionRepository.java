/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface SubmissionRepository {

    Optional<Submission> findById(UUID id);

    Optional<Submission> findByIdForUpdate(UUID id);

    /** The student's last answer to a lesson's homework. */
    Optional<Submission> findLatest(UUID enrollmentId, UUID lessonId);

    /** The student's answers to a lesson's homework, the latest first. */
    List<Submission> findHistory(UUID enrollmentId, UUID lessonId);

    /**
     * A school's answers, those that waited longest first.
     *
     * @param status only answers in that status; {@code null} for any
     * @param courseId only answers of that course; {@code null} for any
     */
    List<Submission> findBySchool(
            UUID schoolId, Submission.@Nullable Status status, @Nullable UUID courseId, int limit, int offset);

    Submission save(Submission submission);
}
