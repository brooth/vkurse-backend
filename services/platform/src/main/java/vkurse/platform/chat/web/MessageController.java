/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.chat.application.MessageService;
import vkurse.platform.chat.domain.BannerType;
import vkurse.platform.chat.domain.Message;
import vkurse.platform.course.web.LinkResponse;
import vkurse.platform.school.web.FileResponse;
import vkurse.platform.school.web.UploadResponse;
import vkurse.platform.storage.domain.StoredFile;

@RestController
@RequestMapping("/chats/{chatId}")
class MessageController {
    private final MessageService messages;

    MessageController(MessageService messages) {
        this.messages = messages;
    }

    /**
     * A page of the feed, oldest first: the latest messages; with {@code before}, those before that message; with
     * {@code after}, those after it (use it to catch up after a stream broke). {@code limit} is 50 by default, 100 at
     * most.
     */
    @GetMapping("/messages")
    List<MessageResponse> list(
            @GetSession UserSession session,
            @PathVariable UUID chatId,
            @RequestParam(required = false) @Nullable UUID before,
            @RequestParam(required = false) @Nullable UUID after,
            @RequestParam(required = false) @Nullable Integer limit) {
        return messages.list(session.userId(), chatId, before, after, limit).stream()
                .map(MessageResponse::of)
                .toList();
    }

    /**
     * Writes a message: text (Markdown), confirmed files, or both. With {@code banner} it is a banner, which only the
     * staff write; {@code banner.pinned} pins it above the feed, until {@code banner.pinnedUntil} if given.
     */
    @PostMapping("/messages")
    MessageResponse post(
            @GetSession UserSession session, @PathVariable UUID chatId, @Valid @RequestBody PostRequest data) {
        var banner = data.banner();
        return MessageResponse.of(messages.post(
                session.userId(),
                chatId,
                data.text(),
                data.fileIds() == null ? List.of() : data.fileIds(),
                data.replyToId(),
                banner == null ? null : banner.type(),
                banner != null && Boolean.TRUE.equals(banner.pinned()),
                banner == null ? null : banner.pinnedUntil()));
    }

    /** The author changes the message's text. */
    @PatchMapping("/messages/{messageId}")
    MessageResponse edit(
            @GetSession UserSession session,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditRequest data) {
        return MessageResponse.of(messages.edit(session.userId(), chatId, messageId, data.text()));
    }

    /** The author, or the staff, delete the message. It stays in the feed as a deleted one. */
    @DeleteMapping("/messages/{messageId}")
    MessageResponse delete(@GetSession UserSession session, @PathVariable UUID chatId, @PathVariable UUID messageId) {
        return MessageResponse.of(messages.delete(session.userId(), chatId, messageId));
    }

    /** Votes the message up (1) or down (-1), or takes the caller's vote back ({@code null}). */
    @PostMapping("/messages/{messageId}/vote")
    MessageResponse vote(
            @GetSession UserSession session,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @Valid @RequestBody VoteRequest data) {
        return MessageResponse.of(messages.vote(session.userId(), chatId, messageId, data.vote()));
    }

    /** The staff pin a banner above the feed, until {@code until} if given. */
    @PostMapping("/messages/{messageId}/pin")
    MessageResponse pin(
            @GetSession UserSession session,
            @PathVariable UUID chatId,
            @PathVariable UUID messageId,
            @Valid @RequestBody PinRequest data) {
        return MessageResponse.of(messages.pin(session.userId(), chatId, messageId, data.until()));
    }

    @PostMapping("/messages/{messageId}/unpin")
    MessageResponse unpin(@GetSession UserSession session, @PathVariable UUID chatId, @PathVariable UUID messageId) {
        return MessageResponse.of(messages.unpin(session.userId(), chatId, messageId));
    }

    /** Signed links to the message's files, by file id. They are in the body, never a redirect. */
    @PostMapping("/messages/{messageId}/links")
    Map<UUID, LinkResponse> links(
            @GetSession UserSession session, @PathVariable UUID chatId, @PathVariable UUID messageId) {
        return LinkResponse.of(messages.links(session.userId(), chatId, messageId));
    }

    /** Step 1 of attaching a file: asks for a link to upload it with. */
    @PostMapping("/files")
    UploadResponse requestUpload(
            @GetSession UserSession session, @PathVariable UUID chatId, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                messages.requestUpload(session.userId(), chatId, data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the file was sent to storage: makes it usable in a message. */
    @PostMapping("/files/{fileId}/confirm")
    FileResponse confirmUpload(@GetSession UserSession session, @PathVariable UUID chatId, @PathVariable UUID fileId) {
        return FileResponse.of(messages.confirmUpload(session.userId(), chatId, fileId));
    }

    record PostRequest(
            @Size(max = Message.MAX_TEXT_LENGTH) @Nullable String text,
            @Size(max = Message.MAX_ATTACHMENTS) @Nullable List<@NotNull UUID> fileIds,
            @Nullable UUID replyToId,
            @Valid @Nullable BannerRequest banner) {}

    record BannerRequest(
            @NotNull BannerType type,
            @Nullable Boolean pinned,
            @Nullable Instant pinnedUntil) {}

    record EditRequest(
            @Size(max = Message.MAX_TEXT_LENGTH) @Nullable String text) {}

    /** {@code vote}: 1, -1, or {@code null} to take the vote back. */
    record VoteRequest(@Nullable Integer vote) {}

    record PinRequest(@Nullable Instant until) {}

    record UploadRequest(
            @NotBlank @Size(max = StoredFile.MAX_NAME_LENGTH)
            String name,

            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Positive Long size) {}
}
