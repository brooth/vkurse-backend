/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.chat.domain.Message;
import vkurse.platform.chat.domain.VoteCount;
import vkurse.platform.school.domain.SchoolRole;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.user.domain.User;

/**
 * A message with what is shown around it.
 *
 * @param author {@code null} if the account is gone
 * @param authorRole the author's role on the school's staff now; {@code null} for a student
 * @param files the attached files that storage still has
 * @param replyTo the message this one answers
 * @param pinned whether the message is pinned above the feed now
 */
public record MessageView(
        Message message,
        @Nullable User author,
        @Nullable SchoolRole authorRole,
        List<StoredFile> files,
        @Nullable Quote replyTo,
        VoteCount votes,
        boolean pinned) {

    /** The start of a quoted message's text; empty if it was deleted. */
    public record Quote(UUID id, @Nullable User author, String text, boolean deleted) {
        static final int MAX_LENGTH = 200;
    }
}
