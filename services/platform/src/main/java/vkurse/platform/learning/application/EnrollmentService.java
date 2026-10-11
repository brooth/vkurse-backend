/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.AccessGrant;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.learning.domain.EnrollmentRepository;
import vkurse.platform.learning.domain.LessonCompletionRepository;
import vkurse.platform.school.application.SchoolStudentService;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/** Who has which course: a student's own courses, and the staff's view of a course's students. */
@Service
@Transactional
public class EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final LessonCompletionRepository completions;
    private final LearningAccess access;
    private final Enroller enroller;
    private final CourseReader reader;
    private final SchoolStudentService students;
    private final UserService users;
    private final LearningNotifier notifier;
    private final Clock clock;

    public EnrollmentService(
            EnrollmentRepository enrollments,
            LessonCompletionRepository completions,
            LearningAccess access,
            Enroller enroller,
            CourseReader reader,
            SchoolStudentService students,
            UserService users,
            LearningNotifier notifier,
            Clock clock) {
        this.enrollments = enrollments;
        this.completions = completions;
        this.access = access;
        this.enroller = enroller;
        this.reader = reader;
        this.students = students;
        this.users = users;
        this.notifier = notifier;
        this.clock = clock;
    }

    /** The courses the caller has now, the latest first, with how far they are in each. */
    @Transactional(readOnly = true)
    public List<MyCourse> listMine(UUID userId, Paging paging) {
        var mine = new ArrayList<MyCourse>();
        for (var enrollment :
                enrollments.findActiveByStudent(userId, clock.instant(), paging.limit(), paging.offset())) {
            reader.material(enrollment.getCourseId()).ifPresent(material -> {
                var curriculum = access.studying(enrollment, material).curriculum();
                mine.add(new MyCourse(
                        enrollment, material.course(), curriculum.lessons().size(), curriculum.completedCount()));
            });
        }
        return mine;
    }

    /** Whether a user has a course now. For the modules that open things to a course's students. */
    @Transactional(readOnly = true)
    public boolean hasCourse(UUID userId, UUID courseId) {
        var now = clock.instant();
        return enrollments
                .find(userId, courseId)
                .filter(found -> found.isActive(now))
                .isPresent();
    }

    /** The courses a user has now, the latest first. */
    @Transactional(readOnly = true)
    public List<UUID> courseIdsOf(UUID userId, int limit) {
        return enrollments.findActiveByStudent(userId, clock.instant(), limit, 0).stream()
                .map(Enrollment::getCourseId)
                .toList();
    }

    /**
     * Gives a course to someone by hand, for some days or with no end. Someone who has no account yet gets one, and
     * they become a student of the school. Someone who has the course gets more of it (see {@link Enrollment#renew}).
     */
    public StudentProgress grant(UUID actorId, UUID courseId, String email, @Nullable Integer accessDays) {
        var course = course(courseId);
        var grant = AccessGrant.byStaff(actorId, accessDays);
        User student;
        try {
            // Checks that the caller is on the staff and may manage students.
            student = students.add(actorId, course.getSchoolId(), email).user();
        } catch (NotFoundException e) {
            throw courseNotFound();
        }
        var enrollment = enroller.give(course.getSchoolId(), courseId, student.getId(), grant);
        notifier.courseGranted(student, course);
        return progress(List.of(enrollment), courseId).getFirst();
    }

    /** Takes a course away from a student. Their progress stays, and comes back if they get the course again. */
    public void revoke(UUID actorId, UUID enrollmentId) {
        var enrollment = enrollments
                .findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new NotFoundException("Enrollment not found"));
        access.staff(actorId, enrollment.getSchoolId(), "Enrollment").ensureCanManageStudents();
        enroller.revoke(enrollment);
    }

    /** Everyone who has or had the course, the latest first, with how far each is. For the staff. */
    @Transactional(readOnly = true)
    public List<StudentProgress> list(UUID actorId, UUID courseId, Paging paging) {
        var course = course(courseId);
        access.staff(actorId, course.getSchoolId(), "Course");
        return progress(enrollments.findByCourse(courseId, paging.limit(), paging.offset()), courseId);
    }

    private List<StudentProgress> progress(List<Enrollment> found, UUID courseId) {
        var published = reader.material(courseId).orElseThrow(EnrollmentService::courseNotFound).lessons().stream()
                .map(Lesson::getId)
                .collect(Collectors.toSet());
        var completed =
                completions.findLessonIds(found.stream().map(Enrollment::getId).toList());
        var now = clock.instant();
        return found.stream()
                .map(enrollment -> new StudentProgress(
                        enrollment,
                        users.findById(enrollment.getStudentId()).orElse(null),
                        published.size(),
                        completed.getOrDefault(enrollment.getId(), Set.of()).stream()
                                .filter(published::contains)
                                .count(),
                        enrollment.isActive(now)))
                .toList();
    }

    private Course course(UUID courseId) {
        return reader.course(courseId).orElseThrow(EnrollmentService::courseNotFound);
    }

    private static NotFoundException courseNotFound() {
        return new NotFoundException("Course not found");
    }

    /** A course the caller has: how many published lessons it has, and how many of them they completed. */
    public record MyCourse(Enrollment enrollment, Course course, long lessons, long completed) {}

    /**
     * A student of a course, for the staff.
     *
     * @param student {@code null} if the account is gone
     * @param active whether they have the course now
     */
    public record StudentProgress(
            Enrollment enrollment, @Nullable User student, long lessons, long completed, boolean active) {}
}
