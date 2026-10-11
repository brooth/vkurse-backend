/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.chat.application.MessageView;
import vkurse.platform.chat.domain.BannerType;
import vkurse.platform.chat.domain.VoteCount;
import vkurse.platform.school.domain.SchoolRole;
import vkurse.platform.school.web.PersonResponse;
import vkurse.platform.storage.domain.StoredFile;

/**
 * A message of a chat. With {@code banner} it is a banner, to be shown so that it stands out. A deleted message has
 * {@code deleted} and nothing in it.
 *
 * @param author who wrote it; {@code null} if the account is gone
 * @param staffRole the author's role on the school's staff now; {@code null} for a student
 * @param pinned whether the banner is pinned above the feed now
 */
public record MessageResponse(
        UUID id,
        Instant createdAt,
        UUID chatId,
        @Nullable PersonResponse author,
        @Nullable SchoolRole staffRole,
        String text,
        List<Attachment> attachments,
        @Nullable Quote replyTo,
        @Nullable BannerType banner,
        boolean pinned,
        @Nullable Instant pinnedUntil,
        @Nullable Instant editedAt,
        boolean deleted,
        Votes votes) {

    public static MessageResponse of(MessageView view) {
        var message = view.message();
        return new MessageResponse(
                message.getId(),
                message.getCreatedAt(),
                message.getChatId(),
                view.author() == null ? null : PersonResponse.of(view.author()),
                view.authorRole(),
                message.getText(),
                view.files().stream().map(Attachment::of).toList(),
                view.replyTo() == null ? null : Quote.of(view.replyTo()),
                message.getBanner(),
                view.pinned(),
                view.pinned() ? message.getPinnedUntil() : null,
                message.getEditedAt(),
                message.isDeleted(),
                Votes.of(view.votes()));
    }

    /** A file attached to the message. Its link is asked for separately ({@code …/links}). */
    public record Attachment(UUID id, String name, String contentType, long size) {
        static Attachment of(StoredFile file) {
            return new Attachment(file.getId(), file.getName(), file.getContentType(), file.getSize());
        }
    }

    /** The message this one answers: the start of its text, or {@code deleted}. */
    public record Quote(UUID id, @Nullable PersonResponse author, String text, boolean deleted) {
        static Quote of(MessageView.Quote quote) {
            return new Quote(
                    quote.id(),
                    quote.author() == null ? null : PersonResponse.of(quote.author()),
                    quote.text(),
                    quote.deleted());
        }
    }

    /** {@code mine} is the caller's own vote (1 or -1); absent for none, and in stream events. */
    public record Votes(long up, long down, @Nullable Integer mine) {
        static Votes of(VoteCount count) {
            return new Votes(count.up(), count.down(), count.mine());
        }
    }
}
