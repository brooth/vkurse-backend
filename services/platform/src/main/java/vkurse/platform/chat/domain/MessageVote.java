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
import vkurse.core.error.Errors.BadRequestException;

/** One person's vote on a message: up (+1) or down (−1). A person has at most one per message. */
@Entity
@Table(name = "chat_message_votes")
public class MessageVote extends AggregateRoot {
    private UUID messageId;

    private UUID chatId;

    private UUID userId;

    /** 1 or -1. */
    private int vote;

    protected MessageVote() {}

    private MessageVote(Message message, UUID userId, int value, Instant now) {
        super(now);
        this.messageId = message.getId();
        this.chatId = message.getChatId();
        this.userId = userId;
        this.vote = value;
    }

    public static MessageVote cast(Message message, UUID userId, int value, Instant now) {
        message.ensureNotDeleted();
        return new MessageVote(message, userId, checked(value), now);
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getChatId() {
        return chatId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getVote() {
        return vote;
    }

    public void change(int value) {
        this.vote = checked(value);
    }

    private static int checked(int value) {
        if (value != 1 && value != -1) {
            throw new BadRequestException("A vote is 1 or -1");
        }
        return value;
    }
}
