/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.learning.domain.LessonCompletion;
import vkurse.platform.learning.domain.LessonCompletionRepository;

@Repository
class JpaLessonCompletionRepository extends JpaAggregateRepository<LessonCompletion>
        implements LessonCompletionRepository {

    JpaLessonCompletionRepository() {
        super(LessonCompletion.class);
    }

    @Override
    public Set<UUID> findLessonIds(UUID enrollmentId) {
        return new HashSet<>(em.createQuery(
                        "SELECT c.lessonId FROM LessonCompletion c WHERE c.enrollmentId = :enrollmentId", UUID.class)
                .setParameter("enrollmentId", enrollmentId)
                .getResultList());
    }

    @Override
    public boolean exists(UUID enrollmentId, UUID lessonId) {
        return em.createQuery("""
                        SELECT COUNT(c) FROM LessonCompletion c
                        WHERE c.enrollmentId = :enrollmentId AND c.lessonId = :lessonId
                        """, Long.class)
                        .setParameter("enrollmentId", enrollmentId)
                        .setParameter("lessonId", lessonId)
                        .getSingleResult()
                > 0;
    }

    @Override
    public Map<UUID, Set<UUID>> findLessonIds(Collection<UUID> enrollmentIds) {
        var byEnrollment = new HashMap<UUID, Set<UUID>>();
        if (enrollmentIds.isEmpty()) {
            return byEnrollment;
        }
        em.createQuery("SELECT c FROM LessonCompletion c WHERE c.enrollmentId IN :ids", LessonCompletion.class)
                .setParameter("ids", enrollmentIds)
                .getResultList()
                .forEach(completion -> byEnrollment
                        .computeIfAbsent(completion.getEnrollmentId(), id -> new HashSet<>())
                        .add(completion.getLessonId()));
        return byEnrollment;
    }
}
