/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.auth.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import vkurse.core.domain.AggregateRoot;
import vkurse.platform.auth.domain.AuthErrors.OtpAttemptsExceededException;
import vkurse.platform.auth.domain.AuthErrors.OtpExpiredException;
import vkurse.platform.auth.domain.AuthErrors.OtpIncorrectException;
import vkurse.platform.auth.domain.AuthErrors.OtpTooFrequentException;
import vkurse.platform.user.domain.Email;

/**
 * A one-time sign-in code sent by email. Only a keyed hash of the code is kept; the client gets the challenge id. A
 * code lives {@link #TTL} and allows {@link #ATTEMPTS} wrong attempts, and a new code for the same email can be
 * requested once per {@link #RESEND_AFTER}.
 */
@Entity
@Table(name = "auth_otp_codes")
public class OtpChallenge extends AggregateRoot {
    public static final Duration TTL = Duration.ofMinutes(7);
    public static final Duration RESEND_AFTER = Duration.ofSeconds(30);
    public static final int ATTEMPTS = 5;
    public static final int CODE_LENGTH = 6;

    @Convert(converter = Email.Converter.class)
    private Email email;

    private String codeHash;

    private int attemptsLeft;

    private Instant expiresAt;

    protected OtpChallenge() {}

    private OtpChallenge(Email email, String codeHash, Instant now) {
        super(now);
        this.email = email;
        this.codeHash = codeHash;
        this.attemptsLeft = ATTEMPTS;
        this.expiresAt = now.plus(TTL);
    }

    /** Issues a challenge for {@code code}, which the caller emails. Only the code's hash is kept. */
    public static OtpChallenge issue(Email email, String code, SecretHasher hasher, Instant now) {
        return new OtpChallenge(email, hash(email, code, hasher), now);
    }

    public Email getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttemptsLeft() {
        return attemptsLeft;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Checks that a new code may be sent to the email of this challenge, the latest one sent to it.
     *
     * @throws OtpTooFrequentException this code was sent less than {@link #RESEND_AFTER} ago
     */
    public void ensureResendAllowed(Instant now) {
        if (now.isBefore(getCreatedAt().plus(RESEND_AFTER))) {
            throw new OtpTooFrequentException();
        }
    }

    /**
     * Checks a code. A wrong one uses up an attempt, so save the challenge even when this throws.
     *
     * @throws OtpExpiredException the challenge is too old
     * @throws OtpAttemptsExceededException no attempts are left
     * @throws OtpIncorrectException the code is wrong
     */
    public void verify(String presentedCode, SecretHasher hasher, Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new OtpExpiredException();
        }
        if (attemptsLeft <= 0) {
            throw new OtpAttemptsExceededException();
        }
        var presentedCodeHash = hash(email, presentedCode.trim(), hasher);
        var matches = MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8), presentedCodeHash.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            attemptsLeft--;
            throw new OtpIncorrectException(attemptsLeft);
        }
    }

    /** The code is hashed together with its email, so a hash is useless for any other address. */
    private static String hash(Email email, String code, SecretHasher hasher) {
        return hasher.hash(email.value() + ":" + code);
    }
}
