/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.web;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.chat.application.ChatService;
import vkurse.platform.course.domain.Course;
import vkurse.platform.school.domain.SchoolRole;

/**
 * A chat as the caller sees it.
 *
 * @param staffRole the caller's role on the school's staff; {@code null} for a student
 * @param pinned the banners pinned above the feed now, the newest first; only when one chat is read
 * @param lastMessage the chat's last message; only in the list of chats
 * @param unread how many messages the caller hasn't read
 * @param lastReadMessageId the caller's read mark; only when one chat is read
 */
public record ChatResponse(
        UUID id,
        CourseSummary course,
        @Nullable SchoolRole staffRole,
        @Nullable List<MessageResponse> pinned,
        @Nullable MessageResponse lastMessage,
        long unread,
        @Nullable UUID lastReadMessageId) {

    public static ChatResponse of(ChatService.ChatView view) {
        return new ChatResponse(
                view.chat().getId(),
                CourseSummary.of(view.course()),
                view.member().role(),
                view.pinned().stream().map(MessageResponse::of).toList(),
                null,
                view.unread(),
                view.lastReadMessageId());
    }

    public static ChatResponse of(ChatService.ChatSummary summary) {
        return new ChatResponse(
                summary.chat().getId(),
                CourseSummary.of(summary.course()),
                null,
                null,
                summary.lastMessage() == null ? null : MessageResponse.of(summary.lastMessage()),
                summary.unread(),
                null);
    }

    /** The course the chat is of. */
    public record CourseSummary(
            UUID id, UUID schoolId, String title, @Nullable String cover) {
        static CourseSummary of(Course course) {
            return new CourseSummary(course.getId(), course.getSchoolId(), course.getTitle(), course.getCover());
        }
    }
}
