/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.error.Errors.ClientException;
import vkurse.core.notification.Content;
import vkurse.core.notification.Message;
import vkurse.core.notification.NotificationService;
import vkurse.core.notification.Target;
import vkurse.platform.auth.domain.AuthErrors.OtpExpiredException;
import vkurse.platform.auth.domain.AuthErrors.OtpIncorrectException;
import vkurse.platform.auth.domain.OtpChallenge;
import vkurse.platform.auth.domain.OtpChallengeRepository;
import vkurse.platform.auth.domain.SecretHasher;
import vkurse.platform.auth.domain.SignInProvider;
import vkurse.platform.user.application.UserService;
import vkurse.platform.user.domain.Email;
import vkurse.platform.user.domain.User;

/** Sign-in with a code sent by email. The rules of a code are in {@link OtpChallenge}. */
@Service
public class OtpSignInService {
    private final OtpChallengeRepository challenges;
    private final SignInService signIn;
    private final UserService users;
    private final SecretHasher hasher;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OtpSignInService(
            OtpChallengeRepository challenges,
            SignInService signIn,
            UserService users,
            SecretHasher hasher,
            NotificationService notifications,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.challenges = challenges;
        this.signIn = signIn;
        this.users = users;
        this.hasher = hasher;
        this.notifications = notifications;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates a code and emails it. The email goes out after the challenge is committed, so no database connection is
     * held while talking to the mail server; if sending fails, the challenge is removed and the request fails.
     */
    public Ticket send(String address) {
        var email = new Email(address);
        var code = hasher.randomDigits(OtpChallenge.CODE_LENGTH);
        var challenge = Objects.requireNonNull(transaction.execute(status -> {
            var now = clock.instant();
            challenges.findLatestByEmail(email).ifPresent(latest -> latest.ensureResendAllowed(now));
            return challenges.save(OtpChallenge.issue(email, code, hasher, now));
        }));
        try {
            // Sent at once, never submitted: the user is waiting, and a stored message would keep the code.
            notifications.send(Message.of(
                    Content.template(
                            "otp-code",
                            User.DEFAULT_LOCALE,
                            Map.of("code", code, "minutes", OtpChallenge.TTL.toMinutes())),
                    Target.email(email.value())));
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(status -> challenges.removeById(challenge.getId()));
            throw e;
        }
        return new Ticket(challenge.getId(), challenge.getExpiresAt());
    }

    /**
     * Checks the code and signs the user in, signing them up on their first sign-in. A wrong code uses up an attempt,
     * so the attempt count is committed even when the call fails.
     */
    @Transactional(noRollbackFor = ClientException.class)
    public Identity signIn(UUID token, String code, @Nullable String userAgent) {
        var challenge = challenges.findByIdForUpdate(token).orElseThrow(OtpExpiredException::new);
        try {
            challenge.verify(code, hasher, clock.instant());
        } catch (OtpIncorrectException e) {
            challenges.save(challenge);
            throw e;
        }
        challenges.remove(challenge);
        var user = users.findOrRegister(challenge.getEmail(), null, null);
        return signIn.signIn(user, SignInProvider.OTP, userAgent);
    }

    /** What the client gets for a sent code: {@code token} identifies it when signing in. */
    public record Ticket(UUID token, Instant expiresAt) {}
}
