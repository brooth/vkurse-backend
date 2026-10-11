/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.BadRequestException;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.chat.domain.BannerType;
import vkurse.platform.chat.domain.ChatBroadcaster;
import vkurse.platform.chat.domain.ChatEvent;
import vkurse.platform.chat.domain.Message;
import vkurse.platform.chat.domain.MessageRepository;
import vkurse.platform.chat.domain.MessageVote;
import vkurse.platform.chat.domain.MessageVoteRepository;
import vkurse.platform.storage.application.StoredFileService;
import vkurse.platform.storage.domain.Download;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.Upload;

/**
 * The messages of a chat, for the people in it. Every change is announced to the chat's open streams in the
 * transaction that makes it. Storage is never called inside a transaction here.
 */
@Service
public class MessageService {
    public static final int DEFAULT_PAGE = 50;
    public static final int MAX_PAGE = 100;

    private final ChatAccess access;
    private final MessageRepository messages;
    private final MessageVoteRepository votes;
    private final MessageViews views;
    private final StoredFileService files;
    private final ChatBroadcaster broadcaster;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public MessageService(
            ChatAccess access,
            MessageRepository messages,
            MessageVoteRepository votes,
            MessageViews views,
            StoredFileService files,
            ChatBroadcaster broadcaster,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.access = access;
        this.messages = messages;
        this.votes = votes;
        this.views = views;
        this.files = files;
        this.broadcaster = broadcaster;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * A page of the chat's feed, oldest first: the latest messages, those before {@code before}, or those after
     * {@code after}.
     */
    public List<MessageView> list(
            UUID userId, UUID chatId, @Nullable UUID before, @Nullable UUID after, @Nullable Integer limit) {
        var size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, MAX_PAGE);
        return Objects.requireNonNull(transaction.execute(status -> {
            access.chat(userId, chatId);
            return views.of(messages.findPage(chatId, before, after, size), userId);
        }));
    }

    /**
     * Writes a message to the chat. With {@code banner} it is a banner, which only the staff write; {@code pin} pins
     * it above the feed, until {@code pinnedUntil} if given.
     *
     * @param fileIds the caller's own confirmed uploads to this chat, not attached to a message yet
     */
    public MessageView post(
            UUID userId,
            UUID chatId,
            @Nullable String text,
            List<UUID> fileIds,
            @Nullable UUID replyToId,
            @Nullable BannerType banner,
            boolean pin,
            @Nullable Instant pinnedUntil) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var in = access.chat(userId, chatId);
            var now = clock.instant();
            var attached = files.getAll(in.chat().getSchoolId(), MessageViews.CHAT_FILES, Set.copyOf(fileIds));
            for (var fileId : attached.keySet()) {
                if (messages.hasAttachment(chatId, fileId)) {
                    throw new BadRequestException("A file is attached to one message only");
                }
            }
            var replyTo = replyToId == null ? null : message(chatId, replyToId, false);
            var recent = messages.countByAuthorSince(chatId, userId, now.minus(Message.FLOOD_WINDOW));
            var message = Message.post(in.chat(), in.member(), text, attached.values(), replyTo, banner, recent, now);
            if (pin) {
                message.pin(in.member(), pinnedUntil, messages.countPinned(chatId, message.getId(), now), now);
            }
            messages.save(message);
            broadcaster.announce(new ChatEvent(ChatEvent.Kind.CREATED, chatId, message.getId()));
            return views.of(message, userId);
        }));
    }

    /** The author changes a message's text. */
    public MessageView edit(UUID userId, UUID chatId, UUID messageId, @Nullable String text) {
        return change(userId, chatId, messageId, ChatEvent.Kind.UPDATED, (in, message) -> {
            message.edit(in.member(), text, clock.instant());
            return List.of();
        });
    }

    /** The author, or the staff, delete a message. Its files go with it. */
    public MessageView delete(UUID userId, UUID chatId, UUID messageId) {
        return change(
                userId,
                chatId,
                messageId,
                ChatEvent.Kind.DELETED,
                (in, message) -> message.delete(in.member(), clock.instant()));
    }

    /** The staff pin a banner above the feed, for good or until a time. */
    public MessageView pin(UUID userId, UUID chatId, UUID messageId, @Nullable Instant until) {
        return change(userId, chatId, messageId, ChatEvent.Kind.UPDATED, (in, message) -> {
            var now = clock.instant();
            message.pin(in.member(), until, messages.countPinned(chatId, messageId, now), now);
            return List.of();
        });
    }

    public MessageView unpin(UUID userId, UUID chatId, UUID messageId) {
        return change(userId, chatId, messageId, ChatEvent.Kind.UPDATED, (in, message) -> {
            message.unpin(in.member());
            return List.of();
        });
    }

    /**
     * Votes a message up (1) or down (-1), changes the caller's vote, or takes it back ({@code null}).
     */
    public MessageView vote(UUID userId, UUID chatId, UUID messageId, @Nullable Integer value) {
        return Objects.requireNonNull(transaction.execute(status -> {
            access.chat(userId, chatId);
            // Locked, so that two votes of one person don't both find none.
            var message = message(chatId, messageId, true);
            var existing = votes.find(messageId, userId).orElse(null);
            if (value == null) {
                if (existing != null) {
                    votes.remove(existing);
                }
            } else if (existing == null) {
                votes.save(MessageVote.cast(message, userId, value, clock.instant()));
            } else {
                message.ensureNotDeleted();
                existing.change(value);
                votes.save(existing);
            }
            broadcaster.announce(new ChatEvent(ChatEvent.Kind.UPDATED, chatId, messageId));
            return views.of(message, userId);
        }));
    }

    /** Step 1 of attaching a file: asks for a link to upload it with. */
    public Upload requestUpload(UUID userId, UUID chatId, String name, String contentType, long size) {
        var schoolId = schoolOf(userId, chatId);
        return files.requestUpload(FilePurpose.CHAT_FILE, schoolId, userId, name, contentType, size);
    }

    /** Step 2, after the file was sent to storage: makes it usable in a message. Only the uploader may. */
    public StoredFile confirmUpload(UUID userId, UUID chatId, UUID fileId) {
        var schoolId = schoolOf(userId, chatId);
        return files.confirm(fileId, userId, FilePurpose.CHAT_FILE, schoolId);
    }

    /** Signed links to a message's files, by file id. They are signed outside a transaction. */
    public Map<UUID, Download> links(UUID userId, UUID chatId, UUID messageId) {
        var attached = Objects.requireNonNull(transaction.execute(status -> {
            var in = access.chat(userId, chatId);
            var message = message(chatId, messageId, false);
            return files.findAll(in.chat().getSchoolId(), MessageViews.CHAT_FILES, message.fileIds());
        }));
        return files.links(attached.values());
    }

    private MessageView change(UUID userId, UUID chatId, UUID messageId, ChatEvent.Kind kind, Change change) {
        return Objects.requireNonNull(transaction.execute(status -> {
            var in = access.chat(userId, chatId);
            var message = message(chatId, messageId, true);
            // Files the message no longer has are deleted with the change.
            change.apply(in, message).forEach(files::delete);
            messages.save(message);
            broadcaster.announce(new ChatEvent(kind, chatId, messageId));
            return views.of(message, userId);
        }));
    }

    private Message message(UUID chatId, UUID messageId, boolean lock) {
        return (lock ? messages.findByIdForUpdate(messageId) : messages.findById(messageId))
                .filter(found -> found.isIn(chatId))
                .orElseThrow(() -> new NotFoundException("Message not found"));
    }

    private UUID schoolOf(UUID userId, UUID chatId) {
        return Objects.requireNonNull(
                transaction.execute(status -> access.chat(userId, chatId).chat().getSchoolId()));
    }

    /** A change of a message by someone in its chat; returns the stored files to delete with it. */
    @FunctionalInterface
    private interface Change {
        List<UUID> apply(InChat in, Message message);
    }
}
