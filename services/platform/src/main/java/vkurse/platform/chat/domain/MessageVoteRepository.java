/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public interface MessageVoteRepository {

    Optional<MessageVote> find(UUID messageId, UUID userId);

    /**
     * The votes on each of those messages, by message id. A message nobody voted on has no entry.
     *
     * @param userId whose own vote to tell; {@code null} for nobody's
     */
    Map<UUID, VoteCount> count(Collection<UUID> messageIds, @Nullable UUID userId);

    MessageVote save(MessageVote vote);

    void remove(MessageVote vote);
}
