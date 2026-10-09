/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.user.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import ru.vkurse.common.persistence.JpaAggregateRepository;
import ru.vkurse.core.user.domain.Email;
import ru.vkurse.core.user.domain.User;
import ru.vkurse.core.user.domain.UserRepository;

@Repository
class JpaUserRepository extends JpaAggregateRepository<User> implements UserRepository {

    JpaUserRepository() {
        super(User.class);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return super.findById(id).filter(user -> !user.isDeleted());
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return em.createQuery("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL", User.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }
}
