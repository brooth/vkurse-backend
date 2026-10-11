/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.chat.domain.Message;
import vkurse.platform.chat.domain.MessageRepository;

@Repository
class JpaMessageRepository extends JpaAggregateRepository<Message> implements MessageRepository {

    JpaMessageRepository() {
        super(Message.class);
    }

    @Override
    public List<Message> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT m FROM Message m WHERE m.id IN :ids", Message.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public List<Message> findPage(UUID chatId, @Nullable UUID before, @Nullable UUID after, int limit) {
        if (after != null) {
            return em.createQuery(
                            "SELECT m FROM Message m WHERE m.chatId = :chatId AND m.id > :after ORDER BY m.id",
                            Message.class)
                    .setParameter("chatId", chatId)
                    .setParameter("after", after)
                    .setMaxResults(limit)
                    .getResultList();
        }
        var jpql = before == null
                ? "SELECT m FROM Message m WHERE m.chatId = :chatId ORDER BY m.id DESC"
                : "SELECT m FROM Message m WHERE m.chatId = :chatId AND m.id < :before ORDER BY m.id DESC";
        var query = em.createQuery(jpql, Message.class).setParameter("chatId", chatId);
        if (before != null) {
            query.setParameter("before", before);
        }
        // Read newest first to take the page's end, shown oldest first.
        return query.setMaxResults(limit).getResultList().reversed();
    }

    @Override
    public List<Message> findPinned(UUID chatId, Instant now) {
        return em.createQuery("""
                        SELECT m FROM Message m
                        WHERE m.chatId = :chatId AND m.pinned = TRUE AND (m.pinnedUntil IS NULL OR m.pinnedUntil > :now)
                        ORDER BY m.id DESC
                        """, Message.class)
                .setParameter("chatId", chatId)
                .setParameter("now", now)
                .getResultList();
    }

    @Override
    public long countPinned(UUID chatId, UUID exceptMessageId, Instant now) {
        return em.createQuery("""
                        SELECT COUNT(m) FROM Message m
                        WHERE m.chatId = :chatId AND m.pinned = TRUE AND (m.pinnedUntil IS NULL OR m.pinnedUntil > :now)
                          AND m.id <> :except
                        """, Long.class)
                .setParameter("chatId", chatId)
                .setParameter("now", now)
                .setParameter("except", exceptMessageId)
                .getSingleResult();
    }

    @Override
    public long countByAuthorSince(UUID chatId, UUID authorId, Instant since) {
        return em.createQuery("""
                        SELECT COUNT(m) FROM Message m
                        WHERE m.chatId = :chatId AND m.authorId = :authorId AND m.createdAt > :since
                        """, Long.class)
                .setParameter("chatId", chatId)
                .setParameter("authorId", authorId)
                .setParameter("since", since)
                .getSingleResult();
    }

    /** Native SQL: JSONB containment is Postgres's own. */
    @Override
    public boolean hasAttachment(UUID chatId, UUID fileId) {
        var probe = "{\"fileIds\":[\"" + fileId + "\"]}";
        return (Boolean) em.createNativeQuery("""
                        SELECT EXISTS (
                            SELECT 1 FROM chat_messages m
                            WHERE m.chat_id = :chatId AND m.attachments @> CAST(:probe AS jsonb))
                        """)
                .setParameter("chatId", chatId)
                .setParameter("probe", probe)
                .getSingleResult();
    }

    /** Native SQL: {@code DISTINCT ON} picks each chat's last row in one pass over the feed's index. */
    @Override
    public Map<UUID, Message> findLast(Collection<UUID> chatIds) {
        var last = new HashMap<UUID, Message>();
        if (chatIds.isEmpty()) {
            return last;
        }
        // The cast names the result type: a native query's rows are untyped.
        @SuppressWarnings("unchecked")
        List<Message> rows = em.createNativeQuery("""
                        SELECT DISTINCT ON (m.chat_id) m.* FROM chat_messages m
                        WHERE m.chat_id IN (:chatIds)
                        ORDER BY m.chat_id, m.id DESC
                        """, Message.class)
                .setParameter("chatIds", chatIds)
                .getResultList();
        rows.forEach(message -> last.put(message.getChatId(), message));
        return last;
    }

    @Override
    public Map<UUID, Long> countUnread(Collection<UUID> chatIds, UUID userId) {
        var unread = new HashMap<UUID, Long>();
        if (chatIds.isEmpty()) {
            return unread;
        }
        em.createQuery("""
                        SELECT m.chatId, COUNT(m) FROM Message m
                        WHERE m.chatId IN :chatIds AND m.authorId <> :userId AND m.deletedAt IS NULL
                          AND NOT EXISTS (
                              SELECT r.id FROM ChatRead r
                              WHERE r.chatId = m.chatId AND r.userId = :userId AND r.lastReadMessageId >= m.id)
                        GROUP BY m.chatId
                        """, Object[].class)
                .setParameter("chatIds", chatIds)
                .setParameter("userId", userId)
                .getResultList()
                .forEach(row -> unread.put((UUID) row[0], (Long) row[1]));
        return unread;
    }
}
