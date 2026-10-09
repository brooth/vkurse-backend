/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import ru.vkurse.common.persistence.JpaAggregateRepository;
import ru.vkurse.core.auth.domain.Session;
import ru.vkurse.core.auth.domain.SessionRepository;

@Repository
class JpaSessionRepository extends JpaAggregateRepository<Session> implements SessionRepository {

    JpaSessionRepository() {
        super(Session.class);
    }

    @Override
    public Optional<Session> findByExchangeCodeHashForUpdate(String codeHash) {
        return em.createQuery("SELECT s FROM Session s WHERE s.exchangeCodeHash = :hash", Session.class)
                .setParameter("hash", codeHash)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Session> findLiveByUser(UUID userId, Instant now, int limit, int offset) {
        return liveByUser(userId, now)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<Session> findLiveByUser(UUID userId, Instant now) {
        return liveByUser(userId, now).getResultList();
    }

    @Override
    public int deleteExpired(Instant now) {
        return em.createQuery("DELETE FROM Session s WHERE s.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }

    private TypedQuery<Session> liveByUser(UUID userId, Instant now) {
        return em.createQuery("""
                        SELECT s FROM Session s
                        WHERE s.userId = :userId AND s.revokedAt IS NULL AND s.expiresAt > :now
                        ORDER BY s.createdAt DESC
                        """, Session.class).setParameter("userId", userId).setParameter("now", now);
    }
}
