/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import vkurse.platform.course.domain.Lesson;
import vkurse.platform.learning.domain.Curriculum;
import vkurse.platform.learning.domain.Enrollment;

/** A published lesson of a course one student has. It may be locked for them. */
record StudyingLesson(Studying studying, Lesson lesson) {

    Enrollment enrollment() {
        return studying.enrollment();
    }

    /** Checks that the student may read the lesson and work on it now. */
    Curriculum.LessonState ensureOpen() {
        return studying.curriculum().ensureOpen(lesson.getId());
    }
}
