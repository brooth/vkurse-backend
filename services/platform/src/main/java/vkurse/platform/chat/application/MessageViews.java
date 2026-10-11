/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import vkurse.platform.chat.domain.Message;
import vkurse.platform.chat.domain.MessageRepository;
import vkurse.platform.chat.domain.MessageVoteRepository;
import vkurse.platform.chat.domain.VoteCount;
import vkurse.platform.school.application.SchoolMemberService;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/** Gathers what is shown around messages: authors, their roles, files, quotes and votes, a few queries per page. */
@Component
class MessageViews {
    static final Set<FilePurpose> CHAT_FILES = Set.of(FilePurpose.CHAT_FILE);

    private final MessageRepository messages;
    private final MessageVoteRepository votes;
    private final UserService users;
    private final SchoolMemberService staff;
    private final StoredFileService files;
    private final Clock clock;

    MessageViews(
            MessageRepository messages,
            MessageVoteRepository votes,
            UserService users,
            SchoolMemberService staff,
            StoredFileService files,
            Clock clock) {
        this.messages = messages;
        this.votes = votes;
        this.users = users;
        this.staff = staff;
        this.files = files;
        this.clock = clock;
    }

    MessageView of(Message message, @Nullable UUID viewerId) {
        return of(List.of(message), viewerId).getFirst();
    }

    /**
     * Views of messages of one school, in their order.
     *
     * @param viewerId whose own votes to tell; {@code null} for nobody's
     */
    List<MessageView> of(List<Message> page, @Nullable UUID viewerId) {
        if (page.isEmpty()) {
            return List.of();
        }
        var schoolId = page.getFirst().getSchoolId();
        var quoted = messages
                .findByIds(page.stream()
                        .map(Message::getReplyToId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Message::getId, Function.identity()));
        var authorIds = new HashSet<UUID>();
        page.forEach(message -> authorIds.add(message.getAuthorId()));
        quoted.values().forEach(message -> authorIds.add(message.getAuthorId()));
        var authors = authors(authorIds);
        var roles = staff.rolesOf(schoolId, authorIds);
        var attached = files.findAll(
                schoolId,
                CHAT_FILES,
                page.stream().flatMap(message -> message.fileIds().stream()).collect(Collectors.toSet()));
        var counts = votes.count(page.stream().map(Message::getId).toList(), viewerId);
        var now = clock.instant();
        return page.stream()
                .map(message -> new MessageView(
                        message,
                        authors.get(message.getAuthorId()),
                        roles.get(message.getAuthorId()),
                        message.fileIds().stream()
                                .map(attached::get)
                                .filter(Objects::nonNull)
                                .toList(),
                        quote(message.getReplyToId() == null ? null : quoted.get(message.getReplyToId()), authors),
                        counts.getOrDefault(message.getId(), VoteCount.NONE),
                        message.isPinned(now)))
                .toList();
    }

    private Map<UUID, User> authors(Collection<UUID> ids) {
        var found = new HashMap<UUID, User>();
        ids.forEach(id -> users.findById(id).ifPresent(user -> found.put(id, user)));
        return found;
    }

    private static MessageView.@Nullable Quote quote(@Nullable Message quoted, Map<UUID, User> authors) {
        if (quoted == null) {
            return null;
        }
        var text = quoted.getText();
        return new MessageView.Quote(
                quoted.getId(),
                authors.get(quoted.getAuthorId()),
                text.length() > MessageView.Quote.MAX_LENGTH ? text.substring(0, MessageView.Quote.MAX_LENGTH) : text,
                quoted.isDeleted());
    }
}
