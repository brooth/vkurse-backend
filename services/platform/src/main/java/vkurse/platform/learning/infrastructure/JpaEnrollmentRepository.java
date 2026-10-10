/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.infrastructure;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.learning.domain.EnrollmentRepository;

@Repository
class JpaEnrollmentRepository extends JpaAggregateRepository<Enrollment> implements EnrollmentRepository {

    JpaEnrollmentRepository() {
        super(Enrollment.class);
    }

    @Override
    public Optional<Enrollment> find(UUID studentId, UUID courseId) {
        return find(studentId, courseId, LockModeType.NONE);
    }

    @Override
    public Optional<Enrollment> findForUpdate(UUID studentId, UUID courseId) {
        return find(studentId, courseId, LockModeType.PESSIMISTIC_WRITE);
    }

    private Optional<Enrollment> find(UUID studentId, UUID courseId, LockModeType lock) {
        return em
                .createQuery(
                        "SELECT e FROM Enrollment e WHERE e.studentId = :studentId AND e.courseId = :courseId",
                        Enrollment.class)
                .setParameter("studentId", studentId)
                .setParameter("courseId", courseId)
                .setLockMode(lock)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Enrollment> findActiveByStudent(UUID studentId, Instant now, int limit, int offset) {
        return em.createQuery("""
                        SELECT e FROM Enrollment e
                        WHERE e.studentId = :studentId AND e.revokedAt IS NULL
                          AND (e.expiresAt IS NULL OR e.expiresAt > :now)
                        ORDER BY e.createdAt DESC, e.id DESC
                        """, Enrollment.class)
                .setParameter("studentId", studentId)
                .setParameter("now", now)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<Enrollment> findByCourse(UUID courseId, int limit, int offset) {
        return em.createQuery(
                        "SELECT e FROM Enrollment e WHERE e.courseId = :courseId ORDER BY e.createdAt DESC, e.id DESC",
                        Enrollment.class)
                .setParameter("courseId", courseId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<Enrollment> findUnrevoked(UUID schoolId, UUID studentId) {
        return em.createQuery("""
                        SELECT e FROM Enrollment e
                        WHERE e.schoolId = :schoolId AND e.studentId = :studentId AND e.revokedAt IS NULL
                        """, Enrollment.class)
                .setParameter("schoolId", schoolId)
                .setParameter("studentId", studentId)
                .getResultList();
    }

    @Override
    public List<Enrollment> findUnrevoked(UUID studentId) {
        return em.createQuery(
                        "SELECT e FROM Enrollment e WHERE e.studentId = :studentId AND e.revokedAt IS NULL",
                        Enrollment.class)
                .setParameter("studentId", studentId)
                .getResultList();
    }
}
