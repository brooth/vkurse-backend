/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.chat.domain.Chat;
import vkurse.platform.chat.domain.ChatMember;
import vkurse.platform.chat.domain.ChatRepository;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Course;
import vkurse.platform.learning.application.EnrollmentService;
import vkurse.platform.school.application.SchoolMemberService;

/**
 * Who is in a chat: the staff of the course's school, and the students who have the course now. It is asked on every
 * request, so someone who loses the course loses the chat. A chat the caller is not in is not found, like one that
 * doesn't exist. Call it inside the caller's transaction.
 */
@Component
class ChatAccess {
    private final ChatRepository chats;
    private final CourseReader courses;
    private final SchoolMemberService staff;
    private final EnrollmentService enrollments;
    private final Clock clock;

    ChatAccess(
            ChatRepository chats,
            CourseReader courses,
            SchoolMemberService staff,
            EnrollmentService enrollments,
            Clock clock) {
        this.chats = chats;
        this.courses = courses;
        this.staff = staff;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    /** A chat by id, with the caller in it. */
    InChat chat(UUID userId, UUID chatId) {
        var chat = chats.findById(chatId).orElseThrow(ChatAccess::notFound);
        var course = courses.course(chat.getCourseId()).orElseThrow(ChatAccess::notFound);
        return new InChat(chat, course, member(userId, course));
    }

    /** The chat of a course, with the caller in it. */
    InChat ofCourse(UUID userId, UUID courseId) {
        var course = courses.course(courseId).orElseThrow(ChatAccess::notFound);
        var member = member(userId, course);
        return new InChat(open(course.getSchoolId(), courseId), course, member);
    }

    /** The chat of a course, opened if the course has none yet. */
    Chat open(UUID schoolId, UUID courseId) {
        var existing = chats.findByCourse(courseId).orElse(null);
        if (existing != null) {
            return existing;
        }
        try {
            return chats.save(Chat.open(schoolId, courseId, clock.instant()));
        } catch (DataIntegrityViolationException raced) {
            // Someone opened it at the same moment; the caller's transaction is spent, and the next request finds it.
            throw notFound();
        }
    }

    private ChatMember member(UUID userId, Course course) {
        var role = staff.rolesOf(course.getSchoolId(), List.of(userId)).get(userId);
        if (role == null && !enrollments.hasCourse(userId, course.getId())) {
            throw notFound();
        }
        return new ChatMember(userId, role);
    }

    static NotFoundException notFound() {
        return new NotFoundException("Chat not found");
    }
}
