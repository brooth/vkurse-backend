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
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.LessonRepository;

@Repository
class JpaLessonRepository extends JpaAggregateRepository<Lesson> implements LessonRepository {

    JpaLessonRepository() {
        super(Lesson.class);
    }

    @Override
    public List<Lesson> findByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT l FROM Lesson l WHERE l.courseId = :courseId ORDER BY l.sectionId, l.position, l.id",
                        Lesson.class)
                .setParameter("courseId", courseId)
                .getResultList();
    }

    @Override
    public List<Lesson> findPublishedByCourse(UUID courseId) {
        return em.createQuery("""
                        SELECT l FROM Lesson l WHERE l.courseId = :courseId AND l.status = :published
                        ORDER BY l.sectionId, l.position, l.id
                        """, Lesson.class)
                .setParameter("courseId", courseId)
                .setParameter("published", Lesson.Status.PUBLISHED)
                .getResultList();
    }

    @Override
    public long countByCourse(UUID courseId) {
        return em.createQuery("SELECT COUNT(l) FROM Lesson l WHERE l.courseId = :courseId", Long.class)
                .setParameter("courseId", courseId)
                .getSingleResult();
    }

    @Override
    public long countPublishedByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(l) FROM Lesson l WHERE l.courseId = :courseId AND l.status = :published",
                        Long.class)
                .setParameter("courseId", courseId)
                .setParameter("published", Lesson.Status.PUBLISHED)
                .getSingleResult();
    }

    @Override
    public long countBySection(UUID sectionId) {
        return em.createQuery("SELECT COUNT(l) FROM Lesson l WHERE l.sectionId = :sectionId", Long.class)
                .setParameter("sectionId", sectionId)
                .getSingleResult();
    }

    @Override
    public int nextPosition(UUID sectionId) {
        return em.createQuery(
                        "SELECT COALESCE(MAX(l.position), -1) + 1 FROM Lesson l WHERE l.sectionId = :sectionId",
                        Integer.class)
                .setParameter("sectionId", sectionId)
                .getSingleResult();
    }

    /** Native SQL: JSONB containment is Postgres's own, and uses the index on {@code content}. */
    @Override
    public boolean usesFile(UUID fileId) {
        var probe = "{\"blocks\":[{\"fileId\":\"" + fileId + "\"}]}";
        return (Boolean) em.createNativeQuery("""
                        SELECT EXISTS (
                            SELECT 1 FROM course_lessons l JOIN courses c ON c.id = l.course_id
                            WHERE c.deleted_at IS NULL AND l.content @> CAST(:probe AS jsonb))
                        """).setParameter("probe", probe).getSingleResult();
    }
}
