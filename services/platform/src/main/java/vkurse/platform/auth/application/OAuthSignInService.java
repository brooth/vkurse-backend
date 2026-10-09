/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.ServerException;
import vkurse.platform.auth.domain.LinkedAccount;
import vkurse.platform.auth.domain.LinkedAccountRepository;
import vkurse.platform.auth.domain.ProviderAccount;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

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
     * with its {@link ProviderAccount#verifiedEmail verified email}.
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
        var user = users.findOrRegister(account.verifiedEmail(), account.name(), account.picture());
        linkedAccounts.save(LinkedAccount.link(user.getId(), account, clock.instant()));
        return user;
    }
}
