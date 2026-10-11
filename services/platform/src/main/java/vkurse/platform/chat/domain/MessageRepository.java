/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface MessageRepository {

    Optional<Message> findById(UUID id);

    Optional<Message> findByIdForUpdate(UUID id);

    List<Message> findByIds(Collection<UUID> ids);

    /**
     * A page of a chat's feed, oldest first. With {@code after}, the messages that follow it; otherwise the latest
     * ones, or those before {@code before}.
     */
    List<Message> findPage(UUID chatId, @Nullable UUID before, @Nullable UUID after, int limit);

    /** The banners pinned in a chat now, the newest first. */
    List<Message> findPinned(UUID chatId, Instant now);

    /** How many banners of a chat are pinned now, not counting one message. */
    long countPinned(UUID chatId, UUID exceptMessageId, Instant now);

    /** How many messages a person wrote to a chat since a moment. */
    long countByAuthorSince(UUID chatId, UUID authorId, Instant since);

    /** Whether a message of the chat has that stored file attached. */
    boolean hasAttachment(UUID chatId, UUID fileId);

    /** The last message of each of those chats, by chat id. */
    Map<UUID, Message> findLast(Collection<UUID> chatIds);

    /**
     * How many messages each of those chats has that the person hasn't read: written by others, not deleted, and after
     * the person's read mark.
     */
    Map<UUID, Long> countUnread(Collection<UUID> chatIds, UUID userId);

    Message save(Message message);
}
