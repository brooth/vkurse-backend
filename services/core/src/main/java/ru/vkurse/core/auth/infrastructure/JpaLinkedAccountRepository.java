/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import ru.vkurse.common.persistence.JpaAggregateRepository;
import ru.vkurse.core.auth.domain.LinkedAccount;
import ru.vkurse.core.auth.domain.LinkedAccountRepository;
import ru.vkurse.core.auth.domain.SignInProvider;

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
