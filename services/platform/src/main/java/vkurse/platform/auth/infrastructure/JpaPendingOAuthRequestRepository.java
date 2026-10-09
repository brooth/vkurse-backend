/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.auth.domain.PendingOAuthRequest;
import vkurse.platform.auth.domain.PendingOAuthRequestRepository;

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
