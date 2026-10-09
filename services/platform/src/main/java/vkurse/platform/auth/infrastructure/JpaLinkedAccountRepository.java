/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.auth.domain.LinkedAccount;
import vkurse.platform.auth.domain.LinkedAccountRepository;
import vkurse.platform.auth.domain.SignInProvider;

@Repository
class JpaLinkedAccountRepository extends JpaAggregateRepository<LinkedAccount> implements LinkedAccountRepository {

    JpaLinkedAccountRepository() {
        super(LinkedAccount.class);
    }

    @Override
    public Optional<LinkedAccount> findBySubject(SignInProvider provider, String subject) {
        return em.createQuery(
                        "SELECT a FROM LinkedAccount a WHERE a.provider = :provider AND a.subject = :subject",
                        LinkedAccount.class)
                .setParameter("provider", provider)
                .setParameter("subject", subject)
                .getResultStream()
                .findFirst();
    }

    @Override
    public int deleteByUser(UUID userId) {
        return em.createQuery("DELETE FROM LinkedAccount a WHERE a.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
