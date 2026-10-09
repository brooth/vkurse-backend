/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.user.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.platform.user.domain.Email;
import space.vkurse.platform.user.domain.User;
import space.vkurse.platform.user.domain.UserRepository;

/** Use cases around users: a caller's own profile, and finding or signing up a user at sign-in. */
@Service
@Transactional
public class UserService {
    private final UserRepository users;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public UserService(UserRepository users, Clock clock, PlatformTransactionManager transactionManager) {
        this.users = users;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public User getMe(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public User updateMe(UUID userId, @Nullable String name, @Nullable String locale, @Nullable String timezone) {
        var user = getMe(userId);
        user.changeProfile(name, locale, timezone);
        return users.save(user);
    }

    public void deleteMe(UUID userId) {
        var user = getMe(userId);
        user.delete(clock.instant());
        users.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID userId) {
        return users.findById(userId);
    }

    /**
     * Returns the user with this email, signing one up if there is none. {@code name} and {@code picture} are only
     * used for a new user.
     *
     * <p>Two sign-ins with the same new email can race. The sign-up runs in its own transaction, so when the unique
     * index rejects the second one, the user the first one created is found and returned.
     */
    public User findOrRegister(Email email, @Nullable String name, @Nullable String picture) {
        return users.findByEmail(email).orElseGet(() -> {
            try {
                return Objects.requireNonNull(newTransaction.execute(
                        status -> users.save(User.register(email, name, picture, clock.instant()))));
            } catch (DataIntegrityViolationException e) {
                return users.findByEmail(email).orElseThrow(() -> e);
            }
        });
    }
}
