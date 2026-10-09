/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.auth.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.core.error.Errors.ServerException;
import space.vkurse.platform.auth.domain.AuthErrors.OAuthEmailRequiredException;
import space.vkurse.platform.auth.domain.LinkedAccount;
import space.vkurse.platform.auth.domain.LinkedAccountRepository;
import space.vkurse.platform.auth.domain.SignInProvider;
import space.vkurse.platform.user.application.UserService;
import space.vkurse.platform.user.domain.Email;
import space.vkurse.platform.user.domain.User;

/** Sign-in with a Google or Apple account the provider has already verified. */
@Service
public class OAuthSignInService {
    private final LinkedAccountRepository linkedAccounts;
    private final UserService users;
    private final SignInService signIn;
    private final Clock clock;

    public OAuthSignInService(
            LinkedAccountRepository linkedAccounts, UserService users, SignInService signIn, Clock clock) {
        this.linkedAccounts = linkedAccounts;
        this.users = users;
        this.signIn = signIn;
        this.clock = clock;
    }

    /**
     * Finds or signs up the user behind a provider account, starts a session and returns its one-time exchange code.
     *
     * <p>A known account ({@code provider} + {@code subject}) is its linked user. An unknown one is linked to the user
     * with the same email, but only if the provider checked that the person owns that email.
     */
    @Transactional
    public String signIn(ProviderAccount account, @Nullable String userAgent) {
        return signIn.startExchange(findOrRegister(account), account.provider(), userAgent);
    }

    private User findOrRegister(ProviderAccount account) {
        var linked = linkedAccounts.findBySubject(account.provider(), account.subject());
        if (linked.isPresent()) {
            return users.findById(linked.get().getUserId())
                    .orElseThrow(() -> new ServerException("Linked account without a user"));
        }
        if (account.email() == null || !account.emailVerified()) {
            throw new OAuthEmailRequiredException();
        }
        var user = users.findOrRegister(new Email(account.email()), account.name(), account.picture());
        linkedAccounts.save(LinkedAccount.link(
                user.getId(), account.provider(), account.subject(), account.email(), clock.instant()));
        return user;
    }

    /** What a provider told us about the person who signed in. */
    public record ProviderAccount(
            SignInProvider provider,
            String subject,
            @Nullable String email,
            boolean emailVerified,
            @Nullable String name,
            @Nullable String picture) {}
}
