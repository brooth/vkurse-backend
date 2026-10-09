/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.user.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.user.domain.Email;
import vkurse.platform.user.domain.User;
import vkurse.platform.user.domain.UserRepository;

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
