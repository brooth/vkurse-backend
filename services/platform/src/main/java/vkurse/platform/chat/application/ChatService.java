/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.core.web.Paging;
import vkurse.platform.chat.domain.Chat;
import vkurse.platform.chat.domain.ChatMember;
import vkurse.platform.chat.domain.ChatRead;
import vkurse.platform.chat.domain.ChatReadRepository;
import vkurse.platform.chat.domain.ChatRepository;
import vkurse.platform.chat.domain.MessageRepository;
import vkurse.platform.course.application.CourseReader;
import vkurse.platform.course.domain.Course;
import vkurse.platform.learning.application.EnrollmentService;
import vkurse.platform.school.application.SchoolMemberService;

/** Chats as the people in them see them: a course's chat, one's own chats, and how far one has read. */
@Service
@Transactional
public class ChatService {
    /** How many of a person's courses are looked at for "my chats". */
    private static final int MAX_COURSES = 500;

    private final ChatAccess access;
    private final ChatRepository chats;
    private final MessageRepository messages;
    private final ChatReadRepository reads;
    private final MessageViews views;
    private final CourseReader courses;
    private final SchoolMemberService staff;
    private final EnrollmentService enrollments;
    private final Clock clock;

    public ChatService(
            ChatAccess access,
            ChatRepository chats,
            MessageRepository messages,
            ChatReadRepository reads,
            MessageViews views,
            CourseReader courses,
            SchoolMemberService staff,
            EnrollmentService enrollments,
            Clock clock) {
        this.access = access;
        this.chats = chats;
        this.messages = messages;
        this.reads = reads;
        this.views = views;
        this.courses = courses;
        this.staff = staff;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    /** The chat of a course, for someone in it. A course made before chats existed gets its chat here. */
    public ChatView forCourse(UUID userId, UUID courseId) {
        return view(access.ofCourse(userId, courseId));
    }

    @Transactional(readOnly = true)
    public ChatView get(UUID userId, UUID chatId) {
        return view(access.chat(userId, chatId));
    }

    /**
     * The chats the caller is in: of the courses they have now, and of every course of the schools they work in. Each
     * comes with its last message and the caller's unread count; within a page, the chat written to last comes first.
     */
    @Transactional(readOnly = true)
    public List<ChatSummary> listMine(UUID userId, Paging paging) {
        var found = chats.findByCoursesOrSchools(
                enrollments.courseIdsOf(userId, MAX_COURSES),
                staff.schoolIdsOf(userId),
                paging.limit(),
                paging.offset());
        var ids = found.stream().map(Chat::getId).toList();
        var last = messages.findLast(ids);
        var unread = messages.countUnread(ids, userId);
        var mine = new ArrayList<ChatSummary>();
        for (var chat : found) {
            // A deleted course's chat is gone with it.
            courses.course(chat.getCourseId()).ifPresent(course -> {
                var lastMessage = last.get(chat.getId());
                mine.add(new ChatSummary(
                        chat,
                        course,
                        lastMessage == null ? null : views.of(lastMessage, userId),
                        unread.getOrDefault(chat.getId(), 0L)));
            });
        }
        mine.sort(Comparator.comparing(ChatSummary::lastActivity).reversed());
        return mine;
    }

    /** Marks the chat read up to a message. A message earlier than the caller's mark changes nothing. */
    public ChatView markRead(UUID userId, UUID chatId, UUID messageId) {
        var in = access.chat(userId, chatId);
        var message = messages.findById(messageId)
                .filter(found -> found.isIn(chatId))
                .orElseThrow(() -> new NotFoundException("Message not found"));
        var read = reads.find(chatId, userId).orElse(null);
        if (read == null) {
            reads.save(ChatRead.start(message, userId, clock.instant()));
        } else if (read.advance(message)) {
            reads.save(read);
        }
        return view(in);
    }

    private ChatView view(InChat in) {
        var chatId = in.chat().getId();
        var userId = in.member().userId();
        return new ChatView(
                in.chat(),
                in.course(),
                in.member(),
                views.of(messages.findPinned(chatId, clock.instant()), userId),
                messages.countUnread(List.of(chatId), userId).getOrDefault(chatId, 0L),
                reads.find(chatId, userId).map(ChatRead::getLastReadMessageId).orElse(null));
    }

    /**
     * A chat as someone in it sees it.
     *
     * @param pinned the banners pinned above the feed now, the newest first
     * @param unread how many messages the person hasn't read
     * @param lastReadMessageId the person's read mark
     */
    public record ChatView(
            Chat chat,
            Course course,
            ChatMember member,
            List<MessageView> pinned,
            long unread,
            @Nullable UUID lastReadMessageId) {}

    /** A chat in the list of one's chats. */
    public record ChatSummary(
            Chat chat, Course course, @Nullable MessageView lastMessage, long unread) {

        /** The id of the last thing in the chat: ids are time-ordered, so it sorts chats by activity. */
        UUID lastActivity() {
            return lastMessage == null
                    ? chat.getId()
                    : Objects.requireNonNull(lastMessage.message()).getId();
        }
    }
}
