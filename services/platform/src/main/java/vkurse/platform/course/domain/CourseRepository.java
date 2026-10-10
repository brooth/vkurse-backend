/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.school.domain.Slug;

/** Courses. A deleted course is never returned. */
public interface CourseRepository {

    Optional<Course> findById(UUID id);

    /** Loads and locks the course: changes to its structure and its publication wait for each other. */
    Optional<Course> findByIdForUpdate(UUID id);

    Optional<Course> findBySchoolAndSlug(UUID schoolId, CourseSlug slug);

    /** All courses of a school, drafts too, newest first. */
    List<Course> findBySchool(UUID schoolId, int limit, int offset);

    /**
     * The catalog: published courses of live schools. With {@code text}, those that match it, best first; without,
     * the latest published first.
     *
     * @param schoolId only the courses of that school; {@code null} for every school
     */
    List<CourseCard> findPublished(@Nullable String text, @Nullable UUID schoolId, int limit, int offset);

    /** A published course of a live school, by the school's and its own address. */
    Optional<CourseCard> findPublished(Slug schoolSlug, CourseSlug slug);

    /** A published course of a live school. */
    Optional<CourseCard> findPublished(UUID id);

    Course save(Course course);
}
