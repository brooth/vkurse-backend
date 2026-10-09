/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.infrastructure;

import java.time.Instant;
import java.util.Optional;
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
    public Optional<OtpChallenge> findLatestByEmail(Email email) {
        return em.createQuery(
                        "SELECT c FROM OtpChallenge c WHERE c.email = :email ORDER BY c.createdAt DESC",
                        OtpChallenge.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultStream()
                .findFirst();
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
