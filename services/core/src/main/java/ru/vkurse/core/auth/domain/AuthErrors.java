/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.core.auth.domain;

import java.util.Map;
import org.springframework.http.HttpStatus;
import ru.vkurse.common.error.Errors.ClientException;

/** The auth module's client errors. */
public final class AuthErrors {

    private AuthErrors() {}

    /** 401 {@code invalid-session}: the access token has expired. The client should call {@code /auth/refresh}. */
    public static class InvalidSessionException extends ClientException {
        public InvalidSessionException(String message) {
            super(HttpStatus.UNAUTHORIZED, "invalid-session", message);
        }
    }

    /** 401 {@code relogin-required}: the token or its session is no longer valid. The client must sign in again. */
    public static class ReloginRequiredException extends ClientException {
        public ReloginRequiredException(String message) {
            super(HttpStatus.UNAUTHORIZED, "relogin-required", message);
        }
    }

    /** 400 {@code exchange-code-invalid}: the one-time code of an OAuth sign-in is wrong, used or expired. */
    public static class ExchangeCodeInvalidException extends ClientException {
        public ExchangeCodeInvalidException() {
            super(HttpStatus.BAD_REQUEST, "exchange-code-invalid", "The sign-in code is invalid or expired");
        }
    }

    /** 429 {@code otp-too-frequent}: a code was sent to this email a moment ago. */
    public static class OtpTooFrequentException extends ClientException {
        public OtpTooFrequentException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "otp-too-frequent", "A code was sent recently, wait a little");
        }
    }

    /** 400 {@code otp-expired}: the code is unknown or too old. */
    public static class OtpExpiredException extends ClientException {
        public OtpExpiredException() {
            super(HttpStatus.BAD_REQUEST, "otp-expired", "The code has expired, request a new one");
        }
    }

    /** 429 {@code otp-attempts-exceeded}: every attempt of this code is used up. */
    public static class OtpAttemptsExceededException extends ClientException {
        public OtpAttemptsExceededException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "otp-attempts-exceeded", "Too many wrong codes, request a new one");
        }
    }

    /** 400 {@code otp-incorrect}, with the attempts left. */
    public static class OtpIncorrectException extends ClientException {
        public OtpIncorrectException(int attemptsLeft) {
            super(
                    HttpStatus.BAD_REQUEST,
                    "otp-incorrect",
                    "The code is incorrect",
                    Map.of("attemptsLeft", attemptsLeft));
        }
    }

    /** 400 {@code oauth-email-required}: the provider gave no email it has verified. */
    public static class OAuthEmailRequiredException extends ClientException {
        public OAuthEmailRequiredException() {
            super(HttpStatus.BAD_REQUEST, "oauth-email-required", "The provider gave no verified email");
        }
    }
}
