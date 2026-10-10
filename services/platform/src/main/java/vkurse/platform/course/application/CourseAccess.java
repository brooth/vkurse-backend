/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.application;

import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseRepository;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.school.domain.SchoolMember;

/**
 * Who may reach a course: the staff of its school. A course of a school the caller doesn't work in is "not found",
 * like one that doesn't exist. Call these in a transaction.
 */
@Component
class CourseAccess {
    private final CourseRepository courses;
    private final SchoolMemberService staff;

    CourseAccess(CourseRepository courses, SchoolMemberService staff) {
        this.courses = courses;
        this.staff = staff;
    }

    /** The course, if the caller is on the staff of its school. */
    Course readable(UUID userId, UUID courseId) {
        var course = courses.findById(courseId).orElseThrow(CourseAccess::notFound);
        member(userId, course);
        return course;
    }

    /** The course, if the caller may edit courses in its school. */
    Course editable(UUID userId, UUID courseId) {
        var course = courses.findById(courseId).orElseThrow(CourseAccess::notFound);
        member(userId, course).ensureCanEditCourses();
        return course;
    }

    /**
     * As {@link #editable}, and locks the course: use it to change the course's structure or what is published in it,
     * so that two such changes don't interleave.
     */
    Course editableLocked(UUID userId, UUID courseId) {
        var course = courses.findByIdForUpdate(courseId).orElseThrow(CourseAccess::notFound);
        member(userId, course).ensureCanEditCourses();
        return course;
    }

    /** The caller as a member of the course's school. */
    SchoolMember member(UUID userId, Course course) {
        try {
            return staff.require(course.getSchoolId(), userId);
        } catch (NotFoundException e) {
            throw notFound();
        }
    }

    static NotFoundException notFound() {
        return new NotFoundException("Course not found");
    }
}
