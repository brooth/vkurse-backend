/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.chat.domain.ChatRead;
import vkurse.platform.chat.domain.ChatReadRepository;

@Repository
class JpaChatReadRepository extends JpaAggregateRepository<ChatRead> implements ChatReadRepository {

    JpaChatReadRepository() {
        super(ChatRead.class);
    }

    @Override
    public Optional<ChatRead> find(UUID chatId, UUID userId) {
        return em
                .createQuery("SELECT r FROM ChatRead r WHERE r.chatId = :chatId AND r.userId = :userId", ChatRead.class)
                .setParameter("chatId", chatId)
                .setParameter("userId", userId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
