/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.platform.learning.domain.AccessGrant;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.learning.domain.EnrollmentRepository;

/** Gives courses to students and takes them away, for the system. Call it inside a transaction. */
@Component
class Enroller {
    private final EnrollmentRepository enrollments;
    private final Clock clock;

    Enroller(EnrollmentRepository enrollments, Clock clock) {
        this.enrollments = enrollments;
        this.clock = clock;
    }

    /** Gives a student a course, or gives it again. Safe to repeat for the same order. */
    Enrollment give(UUID schoolId, UUID courseId, UUID studentId, AccessGrant grant) {
        var now = clock.instant();
        var existing = enrollments.findForUpdate(studentId, courseId).orElse(null);
        if (existing == null) {
            return enrollments.save(Enrollment.open(schoolId, courseId, studentId, grant, now));
        }
        return existing.renew(grant, now) ? enrollments.save(existing) : existing;
    }

    /** Takes a course away if that order is what gave it. */
    void takeBack(UUID studentId, UUID courseId, UUID orderId) {
        enrollments
                .findForUpdate(studentId, courseId)
                .filter(enrollment -> enrollment.isGivenBy(orderId))
                .ifPresent(this::revoke);
    }

    /** Takes away every course of a school a student has. */
    void revokeAll(UUID schoolId, UUID studentId) {
        enrollments.findUnrevoked(schoolId, studentId).forEach(this::revoke);
    }

    /** Takes away every course a user has. */
    void revokeAll(UUID studentId) {
        enrollments.findUnrevoked(studentId).forEach(this::revoke);
    }

    void revoke(Enrollment enrollment) {
        if (enrollment.revoke(clock.instant())) {
            enrollments.save(enrollment);
        }
    }
}
