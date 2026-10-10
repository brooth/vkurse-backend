/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.web.Paging;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseErrors.SlugTakenException;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.course.domain.CourseSlug;
import vkurse.platform.course.domain.LessonRepository;
import vkurse.platform.school.application.SchoolMemberService;

/** Use cases around a course itself, for the staff of its school. */
@Service
@Transactional
public class CourseService {
    private final CourseRepository courses;
    private final LessonRepository lessons;
    private final CourseAccess access;
    private final SchoolMemberService staff;
    private final Clock clock;

    public CourseService(
            CourseRepository courses,
            LessonRepository lessons,
            CourseAccess access,
            SchoolMemberService staff,
            Clock clock) {
        this.courses = courses;
        this.lessons = lessons;
        this.access = access;
        this.staff = staff;
        this.clock = clock;
    }

    /** Starts a course in a school, as a draft. */
    public Course draft(
            UUID userId,
            UUID schoolId,
            String title,
            String slug,
            @Nullable String summary,
            @Nullable String description) {
        staff.require(schoolId, userId).ensureCanEditCourses();
        var course = Course.draft(
                schoolId, title, free(schoolId, new CourseSlug(slug)), summary, description, clock.instant());
        return courses.save(course);
    }

    @Transactional(readOnly = true)
    public Course get(UUID userId, UUID courseId) {
        return access.readable(userId, courseId);
    }

    /** All courses of a school, drafts too. */
    @Transactional(readOnly = true)
    public List<Course> list(UUID userId, UUID schoolId, Paging paging) {
        staff.require(schoolId, userId);
        return courses.findBySchool(schoolId, paging.limit(), paging.offset());
    }

    public Course update(
            UUID userId,
            UUID courseId,
            @Nullable String title,
            @Nullable String slug,
            @Nullable String summary,
            @Nullable String description) {
        var course = access.editable(userId, courseId);
        var newSlug = slug == null ? null : new CourseSlug(slug);
        course.changeDetails(
                title,
                newSlug == null || newSlug.equals(course.getSlug()) ? null : free(course.getSchoolId(), newSlug),
                summary,
                description);
        return courses.save(course);
    }

    /** Puts the course into the catalog. */
    public Course publish(UUID userId, UUID courseId) {
        var course = access.editableLocked(userId, courseId);
        course.publish(lessons.countPublishedByCourse(courseId), clock.instant());
        return courses.save(course);
    }

    public Course unpublish(UUID userId, UUID courseId) {
        var course = access.editableLocked(userId, courseId);
        course.unpublish();
        return courses.save(course);
    }

    public void delete(UUID userId, UUID courseId) {
        var course = courses.findByIdForUpdate(courseId).orElseThrow(CourseAccess::notFound);
        access.member(userId, course).ensureCanDeleteCourses();
        course.delete(clock.instant());
        courses.save(course);
    }

    /**
     * Returns the slug if no course of the school has it. Two requests can still take the same free slug at once: the
     * unique index then rejects the second with a plain 409 {@code conflict}.
     */
    private CourseSlug free(UUID schoolId, CourseSlug slug) {
        if (courses.findBySchoolAndSlug(schoolId, slug).isPresent()) {
            throw new SlugTakenException();
        }
        return slug;
    }
}
