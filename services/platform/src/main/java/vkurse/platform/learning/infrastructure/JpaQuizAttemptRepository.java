/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.learning.domain.QuizAttempt;
import vkurse.platform.learning.domain.QuizAttemptRepository;

@Repository
class JpaQuizAttemptRepository extends JpaAggregateRepository<QuizAttempt> implements QuizAttemptRepository {

    JpaQuizAttemptRepository() {
        super(QuizAttempt.class);
    }

    @Override
    public List<QuizAttempt> findByLesson(UUID enrollmentId, UUID lessonId) {
        return em.createQuery("""
                        SELECT a FROM QuizAttempt a WHERE a.enrollmentId = :enrollmentId AND a.lessonId = :lessonId
                        ORDER BY a.createdAt DESC, a.id DESC
                        """, QuizAttempt.class)
                .setParameter("enrollmentId", enrollmentId)
                .setParameter("lessonId", lessonId)
                .setMaxResults(100)
                .getResultList();
    }

    @Override
    public boolean hasPassed(UUID enrollmentId, UUID lessonId) {
        return em.createQuery("""
                        SELECT COUNT(a) FROM QuizAttempt a
                        WHERE a.enrollmentId = :enrollmentId AND a.lessonId = :lessonId AND a.passed = TRUE
                        """, Long.class)
                        .setParameter("enrollmentId", enrollmentId)
                        .setParameter("lessonId", lessonId)
                        .getSingleResult()
                > 0;
    }
}
