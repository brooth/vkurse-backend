/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.infrastructure;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseCard;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.course.domain.CourseSlug;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.school.domain.Slug;

@Repository
class JpaCourseRepository extends JpaAggregateRepository<Course> implements CourseRepository {
    /** A published course of a live school with its school and the number of its published lessons. */
    private static final String CARDS = """
            SELECT new vkurse.platform.course.domain.CourseCard(
                c, s, (SELECT COUNT(l) FROM Lesson l WHERE l.courseId = c.id AND l.status = :publishedLesson))
            FROM Course c JOIN School s ON s.id = c.schoolId
            WHERE c.status = :published AND c.deletedAt IS NULL AND s.deletedAt IS NULL
            """;

    JpaCourseRepository() {
        super(Course.class);
    }

    @Override
    public Optional<Course> findById(UUID id) {
        return super.findById(id).filter(course -> !course.isDeleted());
    }

    @Override
    public Optional<Course> findByIdForUpdate(UUID id) {
        return super.findByIdForUpdate(id).filter(course -> !course.isDeleted());
    }

    @Override
    public Optional<Course> findBySchoolAndSlug(UUID schoolId, CourseSlug slug) {
        return em
                .createQuery(
                        "SELECT c FROM Course c WHERE c.schoolId = :schoolId AND c.slug = :slug AND c.deletedAt IS NULL",
                        Course.class)
                .setParameter("schoolId", schoolId)
                .setParameter("slug", slug)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Course> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery("""
                        SELECT c FROM Course c WHERE c.schoolId = :schoolId AND c.deletedAt IS NULL
                        ORDER BY c.createdAt DESC, c.id DESC
                        """, Course.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<CourseCard> findPublished(@Nullable String text, @Nullable UUID schoolId, int limit, int offset) {
        if (text != null) {
            return cards(searchIds(text, schoolId, limit, offset));
        }
        var query = em.createQuery(
                        CARDS + (schoolId == null ? "" : " AND c.schoolId = :schoolId")
                                + " ORDER BY c.publishedAt DESC, c.id DESC",
                        CourseCard.class)
                .setParameter("published", Course.Status.PUBLISHED)
                .setParameter("publishedLesson", Lesson.Status.PUBLISHED);
        if (schoolId != null) {
            query.setParameter("schoolId", schoolId);
        }
        return query.setMaxResults(limit).setFirstResult(offset).getResultList();
    }

    @Override
    public Optional<CourseCard> findPublished(Slug schoolSlug, CourseSlug slug) {
        return em
                .createQuery(CARDS + " AND s.slug = :schoolSlug AND c.slug = :slug", CourseCard.class)
                .setParameter("published", Course.Status.PUBLISHED)
                .setParameter("publishedLesson", Lesson.Status.PUBLISHED)
                .setParameter("schoolSlug", schoolSlug)
                .setParameter("slug", slug)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Optional<CourseCard> findPublished(UUID id) {
        return cards(List.of(id)).stream().findFirst();
    }

    /**
     * The ids of the published courses that match a search text, best match first. Native SQL: full-text search is
     * Postgres's own. A course matches by the words of its title, summary and description in any grammatical form
     * (the {@code search} column), or by a piece of its title as it was typed.
     */
    private List<UUID> searchIds(String text, @Nullable UUID schoolId, int limit, int offset) {
        var query = em.createNativeQuery(
                        """
                        SELECT c.id FROM courses c JOIN schools s ON s.id = c.school_id
                        WHERE c.status = 'PUBLISHED' AND c.deleted_at IS NULL AND s.deleted_at IS NULL %s
                          AND (c.search @@ websearch_to_tsquery('russian', :text) OR c.title ILIKE :piece ESCAPE '\\')
                        ORDER BY ts_rank(c.search, websearch_to_tsquery('russian', :text)) DESC,
                                 c.published_at DESC, c.id DESC
                        """.formatted(schoolId == null ? "" : "AND c.school_id = :schoolId"), UUID.class)
                .setParameter("text", text)
                .setParameter("piece", "%" + escapeLike(text) + "%");
        if (schoolId != null) {
            query.setParameter("schoolId", schoolId);
        }
        // The query selects one UUID column, which is what the result class says.
        @SuppressWarnings("unchecked")
        List<UUID> ids = query.setMaxResults(limit).setFirstResult(offset).getResultList();
        return ids;
    }

    /** The cards of those courses, in the order of the ids; a course that is no longer published is left out. */
    private List<CourseCard> cards(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em
                .createQuery(CARDS + " AND c.id IN :ids", CourseCard.class)
                .setParameter("published", Course.Status.PUBLISHED)
                .setParameter("publishedLesson", Lesson.Status.PUBLISHED)
                .setParameter("ids", ids)
                .getResultList()
                .stream()
                .sorted(Comparator.comparingInt(
                        card -> ids.indexOf(card.course().getId())))
                .toList();
    }

    /** Makes the wildcards of LIKE in a search text plain characters. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
