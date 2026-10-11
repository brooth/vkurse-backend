/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.infrastructure;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.chat.domain.MessageVote;
import vkurse.platform.chat.domain.MessageVoteRepository;
import vkurse.platform.chat.domain.VoteCount;

@Repository
class JpaMessageVoteRepository extends JpaAggregateRepository<MessageVote> implements MessageVoteRepository {

    JpaMessageVoteRepository() {
        super(MessageVote.class);
    }

    @Override
    public Optional<MessageVote> find(UUID messageId, UUID userId) {
        return em
                .createQuery(
                        "SELECT v FROM MessageVote v WHERE v.messageId = :messageId AND v.userId = :userId",
                        MessageVote.class)
                .setParameter("messageId", messageId)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public Map<UUID, VoteCount> count(Collection<UUID> messageIds, @Nullable UUID userId) {
        var counts = new HashMap<UUID, VoteCount>();
        if (messageIds.isEmpty()) {
            return counts;
        }
        em.createQuery("SELECT v FROM MessageVote v WHERE v.messageId IN :messageIds", MessageVote.class)
                .setParameter("messageIds", messageIds)
                .getResultList()
                .forEach(vote -> counts.merge(
                        vote.getMessageId(),
                        new VoteCount(
                                vote.getVote() > 0 ? 1 : 0,
                                vote.getVote() < 0 ? 1 : 0,
                                vote.getUserId().equals(userId) ? vote.getVote() : null),
                        (a, b) -> new VoteCount(
                                a.up() + b.up(), a.down() + b.down(), a.mine() != null ? a.mine() : b.mine())));
        return counts;
    }
}
