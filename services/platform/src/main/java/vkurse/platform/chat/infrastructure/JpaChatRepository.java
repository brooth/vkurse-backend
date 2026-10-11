/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.chat.domain.Chat;
import vkurse.platform.chat.domain.ChatRepository;

@Repository
class JpaChatRepository extends JpaAggregateRepository<Chat> implements ChatRepository {
    // An IN list can't be empty; this id is nobody's.
    private static final List<UUID> NOBODY = List.of(new UUID(0, 0));

    JpaChatRepository() {
        super(Chat.class);
    }

    @Override
    public Optional<Chat> findByCourse(UUID courseId) {
        return em
                .createQuery("SELECT c FROM Chat c WHERE c.courseId = :courseId", Chat.class)
                .setParameter("courseId", courseId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Chat> findByCoursesOrSchools(
            Collection<UUID> courseIds, Collection<UUID> schoolIds, int limit, int offset) {
        return em.createQuery("""
                        SELECT c FROM Chat c WHERE c.courseId IN :courseIds OR c.schoolId IN :schoolIds
                        ORDER BY c.createdAt DESC, c.id DESC
                        """, Chat.class)
                .setParameter("courseIds", courseIds.isEmpty() ? NOBODY : courseIds)
                .setParameter("schoolIds", schoolIds.isEmpty() ? NOBODY : schoolIds)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }
}
