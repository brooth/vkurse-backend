/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.course.domain.Section;
import vkurse.platform.course.domain.SectionRepository;

@Repository
class JpaSectionRepository extends JpaAggregateRepository<Section> implements SectionRepository {

    JpaSectionRepository() {
        super(Section.class);
    }

    @Override
    public List<Section> findByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT s FROM Section s WHERE s.courseId = :courseId ORDER BY s.position, s.id", Section.class)
                .setParameter("courseId", courseId)
                .getResultList();
    }

    @Override
    public long countByCourse(UUID courseId) {
        return em.createQuery("SELECT COUNT(s) FROM Section s WHERE s.courseId = :courseId", Long.class)
                .setParameter("courseId", courseId)
                .getSingleResult();
    }

    @Override
    public int nextPosition(UUID courseId) {
        return em.createQuery(
                        "SELECT COALESCE(MAX(s.position), -1) + 1 FROM Section s WHERE s.courseId = :courseId",
                        Integer.class)
                .setParameter("courseId", courseId)
                .getSingleResult();
    }
}
