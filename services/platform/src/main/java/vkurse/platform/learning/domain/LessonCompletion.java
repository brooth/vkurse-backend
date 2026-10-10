/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vkurse.core.domain.AggregateRoot;

/** A student completed a lesson of a course they have. Once per enrollment and lesson, and never undone. */
@Entity
@Table(name = "lesson_completions")
public class LessonCompletion extends AggregateRoot {
    private UUID enrollmentId;

    private UUID courseId;

    private UUID lessonId;

    private UUID studentId;

    protected LessonCompletion() {}

    private LessonCompletion(Enrollment enrollment, UUID lessonId, Instant now) {
        super(now);
        this.enrollmentId = enrollment.getId();
        this.courseId = enrollment.getCourseId();
        this.lessonId = lessonId;
        this.studentId = enrollment.getStudentId();
    }

    public static LessonCompletion record(Enrollment enrollment, UUID lessonId, Instant now) {
        var completion = new LessonCompletion(enrollment, lessonId, now);
        completion.raise(new LessonCompleted(enrollment.getId(), lessonId, enrollment.getStudentId()));
        return completion;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public UUID getStudentId() {
        return studentId;
    }
}
