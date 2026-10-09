/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import ru.vkurse.common.domain.AggregateRoot;
import ru.vkurse.core.auth.domain.AuthErrors.OtpAttemptsExceededException;
import ru.vkurse.core.auth.domain.AuthErrors.OtpExpiredException;
import ru.vkurse.core.auth.domain.AuthErrors.OtpIncorrectException;
import ru.vkurse.core.user.domain.Email;

/**
 * A one-time sign-in code sent by email. Only a keyed hash of the code is kept; the client gets the challenge id. A
 * code lives {@link #TTL} and allows {@link #ATTEMPTS} wrong attempts, and a new code for the same email can be
 * requested once per {@link #RESEND_AFTER}.
 */
@Entity
@Table(name = "auth_otp_codes")
@Getter
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

    public static OtpChallenge issue(Email email, String codeHash, Instant now) {
        return new OtpChallenge(email, codeHash, now);
    }

    /**
     * Checks a code. A wrong one uses up an attempt, so save the challenge even when this throws.
     *
     * @throws OtpExpiredException the challenge is too old
     * @throws OtpAttemptsExceededException no attempts are left
     * @throws OtpIncorrectException the code is wrong
     */
    public void verify(String presentedCodeHash, Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new OtpExpiredException();
        }
        if (attemptsLeft <= 0) {
            throw new OtpAttemptsExceededException();
        }
        var matches = MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8), presentedCodeHash.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            attemptsLeft--;
            throw new OtpIncorrectException(attemptsLeft);
        }
    }
}
