/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.learning.domain.Curriculum;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.learning.domain.EnrollmentRepository;
import vkurse.platform.learning.domain.LessonCompletionRepository;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.school.domain.SchoolMember;

/**
 * Who may reach what in this module: a student, the courses they have now; the staff, their school. Call it inside
 * the caller's transaction.
 */
@Component
class LearningAccess {
    private final EnrollmentRepository enrollments;
    private final LessonCompletionRepository completions;
    private final CourseReader reader;
    private final SchoolMemberService staff;
    private final Clock clock;

    LearningAccess(
            EnrollmentRepository enrollments,
            LessonCompletionRepository completions,
            CourseReader reader,
            SchoolMemberService staff,
            Clock clock) {
        this.enrollments = enrollments;
        this.completions = completions;
        this.reader = reader;
        this.staff = staff;
        this.clock = clock;
    }

    /**
     * A course as the caller has it now. A course they don't have, or no longer have, is not found.
     *
     * @param lock locks the enrollment, for a change to the student's progress
     */
    Studying course(UUID studentId, UUID courseId, boolean lock) {
        var now = clock.instant();
        var enrollment = (lock ? enrollments.findForUpdate(studentId, courseId) : enrollments.find(studentId, courseId))
                .filter(found -> found.isActive(now))
                .orElseThrow(() -> new NotFoundException("Course not found"));
        var material = reader.material(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
        return studying(enrollment, material);
    }

    /** A published lesson of a course the caller has now. It may be locked: ask {@link StudyingLesson#ensureOpen}. */
    StudyingLesson lesson(UUID studentId, UUID lessonId, boolean lock) {
        var lesson = reader.publishedLesson(lessonId).orElseThrow(LearningAccess::lessonNotFound);
        try {
            return new StudyingLesson(course(studentId, lesson.getCourseId(), lock), lesson);
        } catch (NotFoundException e) {
            throw lessonNotFound();
        }
    }

    Studying studying(Enrollment enrollment, CourseReader.Material material) {
        var curriculum = Curriculum.of(
                material.sections(),
                material.lessons(),
                enrollment,
                completions.findLessonIds(enrollment.getId()),
                clock.instant());
        return new Studying(enrollment, material, curriculum);
    }

    /**
     * The caller as a member of the school's staff.
     *
     * @param what the thing the caller asked for, to name in "… not found"
     */
    SchoolMember staff(UUID userId, UUID schoolId, String what) {
        try {
            return staff.require(schoolId, userId);
        } catch (NotFoundException e) {
            throw new NotFoundException(what + " not found");
        }
    }

    private static NotFoundException lessonNotFound() {
        return new NotFoundException("Lesson not found");
    }
}
