/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.infrastructure;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import space.vkurse.core.persistence.JpaAggregateRepository;
import space.vkurse.platform.auth.domain.OtpChallenge;
import space.vkurse.platform.auth.domain.OtpChallengeRepository;
import space.vkurse.platform.user.domain.Email;

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
