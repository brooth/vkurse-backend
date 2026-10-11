/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.error.Errors.ForbiddenException;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.chat.domain.ChatErrors.MessageDeletedException;
import vkurse.platform.chat.domain.ChatErrors.TooManyMessagesException;
import vkurse.platform.chat.domain.ChatErrors.TooManyPinsException;
import vkurse.platform.storage.domain.StoredFile;

/**
 * One message of a chat. A message with a {@link BannerType} is a banner: the staff's word to the whole course, shown
 * so that it stands out, and pinned above the feed if the staff pin it.
 *
 * <p>A deleted message keeps its row and its place in the feed, with nothing in it, so that replies to it and the
 * order of the feed stay as they were. Ids are time-ordered, so a chat's feed is its messages by id.
 */
@Entity
@Table(name = "chat_messages")
public class Message extends AggregateRoot {
    public static final int MAX_TEXT_LENGTH = 4000;
    public static final int MAX_ATTACHMENTS = 10;

    /** How many banners a chat may have pinned at once. */
    public static final int MAX_PINNED = 3;

    /** A person writes to a chat at most {@link #MAX_PER_WINDOW} times in this long. */
    public static final Duration FLOOD_WINDOW = Duration.ofMinutes(1);

    public static final int MAX_PER_WINDOW = 30;

    private UUID chatId;

    private UUID schoolId;

    private UUID authorId;

    /** Markdown, never HTML; empty when the message is only files, or deleted. */
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    private Attachments attachments;

    /** The message this one answers. */
    private @Nullable UUID replyToId;

    /** Set for a banner. */
    @Enumerated(EnumType.STRING)
    private @Nullable BannerType banner;

    private boolean pinned;

    /** When the pin ends by itself; {@code null} for a pin with no end. */
    private @Nullable Instant pinnedUntil;

    private @Nullable Instant editedAt;

    private @Nullable Instant deletedAt;

    protected Message() {}

    private Message(
            Chat chat,
            UUID authorId,
            String text,
            Attachments attachments,
            @Nullable UUID replyToId,
            @Nullable BannerType banner,
            Instant now) {
        super(now);
        this.chatId = chat.getId();
        this.schoolId = chat.getSchoolId();
        this.authorId = authorId;
        this.text = text;
        this.attachments = attachments;
        this.replyToId = replyToId;
        this.banner = banner;
    }

    /**
     * Writes a message to a chat: text, files, or both. With a {@code banner} type it is a banner, which only the
     * staff write and which needs text.
     *
     * @param files the stored files to attach; each must be the author's own upload
     * @param replyTo the message this one answers
     * @param recent how many messages the author wrote to the chat within {@link #FLOOD_WINDOW}
     */
    public static Message post(
            Chat chat,
            ChatMember author,
            @Nullable String text,
            Collection<StoredFile> files,
            @Nullable Message replyTo,
            @Nullable BannerType banner,
            long recent,
            Instant now) {
        if (recent >= MAX_PER_WINDOW) {
            throw new TooManyMessagesException();
        }
        var body = checkedText(text);
        if (banner != null) {
            author.ensureStaff();
            if (body.isEmpty()) {
                throw new BadRequestException("A banner needs text");
            }
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw new BadRequestException("A message has at most " + MAX_ATTACHMENTS + " files");
        }
        if (body.isEmpty() && files.isEmpty()) {
            throw new BadRequestException("Write a message or attach a file");
        }
        for (var file : files) {
            if (!file.getUploadedBy().equals(author.userId())) {
                throw new NotFoundException("File not found");
            }
        }
        if (replyTo != null) {
            if (!replyTo.chatId.equals(chat.getId())) {
                throw new NotFoundException("Message not found");
            }
            replyTo.ensureNotDeleted();
        }
        var fileIds = files.stream().map(StoredFile::getId).distinct().toList();
        return new Message(
                chat,
                author.userId(),
                body,
                new Attachments(fileIds),
                replyTo == null ? null : replyTo.getId(),
                banner,
                now);
    }

    public UUID getChatId() {
        return chatId;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public @Nullable UUID getReplyToId() {
        return replyToId;
    }

    public @Nullable BannerType getBanner() {
        return banner;
    }

    public @Nullable Instant getPinnedUntil() {
        return pinnedUntil;
    }

    public @Nullable Instant getEditedAt() {
        return editedAt;
    }

    public @Nullable Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isIn(UUID chatId) {
        return this.chatId.equals(chatId);
    }

    public boolean isBy(UUID userId) {
        return authorId.equals(userId);
    }

    public boolean isBanner() {
        return banner != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public List<UUID> fileIds() {
        return attachments.fileIds();
    }

    /** Whether the message is pinned above the feed now. */
    public boolean isPinned(Instant now) {
        return pinned && (pinnedUntil == null || now.isBefore(pinnedUntil));
    }

    /** Only its author changes a message's text. A message with no files can't lose its text. */
    public void edit(ChatMember by, @Nullable String text, Instant now) {
        if (!isBy(by.userId())) {
            throw new ForbiddenException("Only the author edits a message");
        }
        ensureNotDeleted();
        var body = checkedText(text);
        if (body.isEmpty() && (attachments.fileIds().isEmpty() || isBanner())) {
            throw new BadRequestException("A message can't be empty");
        }
        this.text = body;
        this.editedAt = now;
    }

    /**
     * Deletes the message: its author may, and so may the staff. The row stays, emptied. Deleting a deleted message
     * changes nothing.
     *
     * @return the stored files the message had, which are the caller's to delete
     */
    public List<UUID> delete(ChatMember by, Instant now) {
        if (!isBy(by.userId())) {
            by.ensureStaff();
        }
        if (isDeleted()) {
            return List.of();
        }
        var files = attachments.fileIds();
        this.text = "";
        this.attachments = Attachments.NONE;
        this.pinned = false;
        this.pinnedUntil = null;
        this.deletedAt = now;
        return files;
    }

    /**
     * The staff pin a banner above the feed, for good or until a time.
     *
     * @param pinnedNow how many other banners of the chat are pinned now
     */
    public void pin(ChatMember by, @Nullable Instant until, long pinnedNow, Instant now) {
        by.ensureStaff();
        ensureNotDeleted();
        if (!isBanner()) {
            throw new BadRequestException("Only a banner can be pinned");
        }
        if (until != null && !until.isAfter(now)) {
            throw new BadRequestException("A pin ends in the future");
        }
        if (pinnedNow >= MAX_PINNED) {
            throw new TooManyPinsException();
        }
        this.pinned = true;
        this.pinnedUntil = until;
    }

    public void unpin(ChatMember by) {
        by.ensureStaff();
        this.pinned = false;
        this.pinnedUntil = null;
    }

    /** A deleted message can't be changed, voted on, pinned or replied to. */
    public void ensureNotDeleted() {
        if (isDeleted()) {
            throw new MessageDeletedException();
        }
    }

    private static String checkedText(@Nullable String text) {
        var body = text == null ? "" : text.strip();
        if (body.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("A message has at most " + MAX_TEXT_LENGTH + " characters");
        }
        return body;
    }
}
