/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import ru.vkurse.common.persistence.JpaAggregateRepository;
import ru.vkurse.core.auth.domain.PendingOAuthRequest;
import ru.vkurse.core.auth.domain.PendingOAuthRequestRepository;

@Repository
class JpaPendingOAuthRequestRepository extends JpaAggregateRepository<PendingOAuthRequest>
        implements PendingOAuthRequestRepository {

    JpaPendingOAuthRequestRepository() {
        super(PendingOAuthRequest.class);
    }

    @Override
    public Optional<PendingOAuthRequest> findByState(String state) {
        return em.createQuery("SELECT r FROM PendingOAuthRequest r WHERE r.state = :state", PendingOAuthRequest.class)
                .setParameter("state", state)
                .getResultStream()
                .findFirst();
    }

    @Override
    public int deleteExpired(Instant now) {
        return em.createQuery("DELETE FROM PendingOAuthRequest r WHERE r.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
