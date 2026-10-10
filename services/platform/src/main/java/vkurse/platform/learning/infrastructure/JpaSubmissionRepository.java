/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.learning.domain.SubmissionRepository;

@Repository
class JpaSubmissionRepository extends JpaAggregateRepository<Submission> implements SubmissionRepository {

    JpaSubmissionRepository() {
        super(Submission.class);
    }

    @Override
    public Optional<Submission> findLatest(UUID enrollmentId, UUID lessonId) {
        return history(enrollmentId, lessonId, 1).stream().findFirst();
    }

    @Override
    public List<Submission> findHistory(UUID enrollmentId, UUID lessonId) {
        return history(enrollmentId, lessonId, 100);
    }

    private List<Submission> history(UUID enrollmentId, UUID lessonId, int limit) {
        return em.createQuery("""
                        SELECT s FROM Submission s WHERE s.enrollmentId = :enrollmentId AND s.lessonId = :lessonId
                        ORDER BY s.createdAt DESC, s.id DESC
                        """, Submission.class)
                .setParameter("enrollmentId", enrollmentId)
                .setParameter("lessonId", lessonId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<Submission> findBySchool(
            UUID schoolId, Submission.@Nullable Status status, @Nullable UUID courseId, int limit, int offset) {
        var jpql = new StringBuilder("SELECT s FROM Submission s WHERE s.schoolId = :schoolId");
        if (status != null) {
            jpql.append(" AND s.status = :status");
        }
        if (courseId != null) {
            jpql.append(" AND s.courseId = :courseId");
        }
        jpql.append(" ORDER BY s.createdAt, s.id");
        var query = em.createQuery(jpql.toString(), Submission.class).setParameter("schoolId", schoolId);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (courseId != null) {
            query.setParameter("courseId", courseId);
        }
        return query.setMaxResults(limit).setFirstResult(offset).getResultList();
    }
}
