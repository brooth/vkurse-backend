/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.application;

import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.error.Errors.ClientException;
import vkurse.platform.auth.AuthProperties;
import vkurse.platform.auth.domain.AuthErrors.ExchangeCodeInvalidException;
import vkurse.platform.auth.domain.AuthErrors.ReloginRequiredException;
import vkurse.platform.auth.domain.SecretHasher;
import vkurse.platform.auth.domain.Session;
import vkurse.platform.auth.domain.SessionRepository;
import vkurse.platform.auth.domain.SignInProvider;
import vkurse.platform.auth.domain.TokenIssuer;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.User;

/** Session lifecycle: sign-in, OAuth exchange codes and refresh-token rotation. */
@Service
public class SignInService {
    private static final Logger log = LoggerFactory.getLogger(SignInService.class);

    private final SessionRepository sessions;
    private final UserService users;
    private final TokenIssuer tokens;
    private final SecretHasher hasher;
    private final AuthProperties properties;
    private final Clock clock;

    public SignInService(
            SessionRepository sessions,
            UserService users,
            TokenIssuer tokens,
            SecretHasher hasher,
            AuthProperties properties,
            Clock clock) {
        this.sessions = sessions;
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a session for a user who just proved who they are, and returns its tokens. */
    @Transactional
    public Identity signIn(User user, SignInProvider provider, @Nullable String userAgent) {
        var session = Session.start(user.getId(), provider, userAgent, clock.instant(), properties.refreshTtl());
        return identity(sessions.save(session), user);
    }

    /**
     * Starts a session for an OAuth sign-in and returns a one-time code for it. The browser is redirected to the
     * frontend with this code, which the frontend trades for tokens with {@link #exchange}. Tokens never appear in a
     * URL.
     */
    @Transactional
    public String startExchange(User user, SignInProvider provider, @Nullable String userAgent) {
        var now = clock.instant();
        var code = hasher.randomSecret();
        var session = Session.start(user.getId(), provider, userAgent, now, properties.refreshTtl());
        session.issueExchangeCode(hasher.hash(code), now);
        sessions.save(session);
        return code;
    }

    /** Trades the one-time code of an OAuth sign-in for tokens. The code works once. */
    @Transactional(noRollbackFor = ClientException.class)
    public Identity exchange(String code) {
        var codeHash = hasher.hash(code);
        var session = sessions.findByExchangeCodeHashForUpdate(codeHash).orElseThrow(ExchangeCodeInvalidException::new);
        if (!session.redeemExchangeCode(codeHash, clock.instant())) {
            throw new ExchangeCodeInvalidException();
        }
        sessions.save(session);
        return identity(session, owner(session));
    }

    /**
     * Trades a refresh token for new tokens. Each refresh token works once: a replayed one means someone else has a
     * copy, so the session is revoked and both parties must sign in again.
     */
    @Transactional(noRollbackFor = ReloginRequiredException.class)
    public Identity refresh(String refreshToken) {
        var claims = tokens.parseRefresh(refreshToken)
                .orElseThrow(() -> new ReloginRequiredException("Invalid refresh token"));
        // The row lock makes two refreshes with the same token wait for each other: the second one is a replay.
        var session = sessions.findByIdForUpdate(claims.sessionId())
                .orElseThrow(() -> new ReloginRequiredException("The session has ended, sign in again"));

        var rotation = session.rotate(claims.generation(), clock.instant(), properties.refreshTtl());
        sessions.save(session);
        if (rotation != Session.Rotation.ROTATED) {
            if (rotation == Session.Rotation.REPLAYED) {
                log.warn("Refresh token of session {} was replayed, the session is revoked", session.getId());
            }
            throw new ReloginRequiredException("The session has ended, sign in again");
        }
        return identity(session, owner(session));
    }

    private Identity identity(Session session, User user) {
        var access = tokens.access(
                user.getId(),
                session.getId(),
                user.getRole().name(),
                clock.instant().plus(properties.accessTtl()));
        var refresh =
                tokens.refresh(user.getId(), session.getId(), session.getRefreshGeneration(), session.getExpiresAt());
        return new Identity(access, refresh, session.getId(), user);
    }

    private User owner(Session session) {
        return users.findById(session.getUserId())
                .orElseThrow(() -> new ReloginRequiredException("The user no longer exists"));
    }
}
