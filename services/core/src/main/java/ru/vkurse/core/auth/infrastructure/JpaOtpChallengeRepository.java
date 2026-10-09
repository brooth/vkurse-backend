/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import ru.vkurse.common.persistence.JpaAggregateRepository;
import ru.vkurse.core.auth.domain.OtpChallenge;
import ru.vkurse.core.auth.domain.OtpChallengeRepository;
import ru.vkurse.core.user.domain.Email;

@Repository
class JpaOtpChallengeRepository extends JpaAggregateRepository<OtpChallenge> implements OtpChallengeRepository {

    JpaOtpChallengeRepository() {
        super(OtpChallenge.class);
    }

    @Override
    public boolean existsIssuedAfter(Email email, Instant since) {
        var count = em.createQuery(
                        "SELECT COUNT(c) FROM OtpChallenge c WHERE c.email = :email AND c.createdAt > :since",
                        Long.class)
                .setParameter("email", email)
                .setParameter("since", since)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public void removeById(UUID id) {
        em.createQuery("DELETE FROM OtpChallenge c WHERE c.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public int deleteExpired(Instant now) {
        return em.createQuery("DELETE FROM OtpChallenge c WHERE c.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
