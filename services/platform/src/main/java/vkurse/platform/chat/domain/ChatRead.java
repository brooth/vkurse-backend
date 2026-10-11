/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vkurse.core.domain.AggregateRoot;

/**
 * How far one person has read a chat: the last message they saw. The messages after it that others wrote are their
 * unread ones. It only moves forward.
 */
@Entity
@Table(name = "chat_reads")
public class ChatRead extends AggregateRoot {
    private UUID chatId;

    private UUID userId;

    private UUID lastReadMessageId;

    protected ChatRead() {}

    private ChatRead(UUID chatId, UUID userId, UUID messageId, Instant now) {
        super(now);
        this.chatId = chatId;
        this.userId = userId;
        this.lastReadMessageId = messageId;
    }

    public static ChatRead start(Message message, UUID userId, Instant now) {
        return new ChatRead(message.getChatId(), userId, message.getId(), now);
    }

    public UUID getChatId() {
        return chatId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    /**
     * Moves the mark to a later message of the chat. An earlier one changes nothing.
     *
     * @return whether the mark moved
     */
    public boolean advance(Message message) {
        // Ids are time-ordered, so a later message has a greater id.
        if (!message.isIn(chatId) || message.getId().compareTo(lastReadMessageId) <= 0) {
            return false;
        }
        lastReadMessageId = message.getId();
        return true;
    }
}
