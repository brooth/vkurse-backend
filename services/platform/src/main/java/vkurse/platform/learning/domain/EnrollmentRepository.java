/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository {

    Optional<Enrollment> findById(UUID id);

    Optional<Enrollment> findByIdForUpdate(UUID id);

    /** The one enrollment of a student in a course, whether its access lasts or not. */
    Optional<Enrollment> find(UUID studentId, UUID courseId);

    /** As {@link #find}, locked until the transaction ends. */
    Optional<Enrollment> findForUpdate(UUID studentId, UUID courseId);

    /** The courses a student has now, the latest first. */
    List<Enrollment> findActiveByStudent(UUID studentId, Instant now, int limit, int offset);

    /** Everyone who has or had a course, the latest first. */
    List<Enrollment> findByCourse(UUID courseId, int limit, int offset);

    /** A student's enrollments in a school's courses that are not taken away. */
    List<Enrollment> findUnrevoked(UUID schoolId, UUID studentId);

    /** A student's enrollments that are not taken away, in every school. */
    List<Enrollment> findUnrevoked(UUID studentId);

    Enrollment save(Enrollment enrollment);
}
