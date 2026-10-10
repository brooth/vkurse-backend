/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.application;

import java.util.Map;
import org.springframework.stereotype.Component;
import vkurse.core.notification.Content;
import vkurse.core.notification.Message;
import vkurse.core.notification.NotificationService;
import vkurse.core.notification.Target;
import vkurse.platform.PlatformProperties;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Course;
import vkurse.platform.learning.domain.Submission;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/** The emails of this module. Each is stored with the caller's changes and delivered by a job. */
@Component
class LearningNotifier {
    private final NotificationService notifications;
    private final UserService users;
    private final CourseReader reader;
    private final PlatformProperties properties;

    LearningNotifier(
            NotificationService notifications, UserService users, CourseReader reader, PlatformProperties properties) {
        this.notifications = notifications;
        this.users = users;
        this.reader = reader;
        this.properties = properties;
    }

    /** Tells a student that the staff gave them a course. */
    void courseGranted(User student, Course course) {
        notifications.submit(Message.of(
                Content.template(
                        "course-granted",
                        student.getLocale(),
                        Map.of(
                                "name", student.getName(),
                                "courseTitle", course.getTitle(),
                                "link", properties.webUrl().toString())),
                Target.email(student.getEmail().value())));
    }

    /** Tells a student that their homework was accepted or given back. Nothing is sent if the lesson is gone. */
    void homeworkReviewed(Submission submission) {
        var student = users.findById(submission.getStudentId()).orElse(null);
        var lesson = reader.lesson(submission.getLessonId()).orElse(null);
        var course = reader.course(submission.getCourseId()).orElse(null);
        if (student == null || lesson == null || course == null) {
            return;
        }
        var comment = submission.getComment();
        notifications.submit(Message.of(
                Content.template(
                        submission.isAccepted() ? "homework-accepted" : "homework-returned",
                        student.getLocale(),
                        Map.of(
                                "name", student.getName(),
                                "lessonTitle", lesson.getTitle(),
                                "courseTitle", course.getTitle(),
                                "comment", comment == null ? "" : comment,
                                "link", properties.webUrl().toString())),
                Target.email(student.getEmail().value())));
    }
}
