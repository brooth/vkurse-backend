/*
 * Copyright (C) VKurse - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package ru.vkurse.common.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Exceptions that reach the client. Each client exception carries its HTTP status and a stable, machine-readable
 * {@code code} (kebab-case, optionally namespaced: {@code payment-failed/low-amount}). {@link ErrorHandler} turns them
 * into RFC 9457 problem bodies.
 */
public final class Errors {

    private Errors() {}

    /** Base class for errors caused by the client's request. Its message is shown to the client. */
    public static class ClientException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        private final Map<String, Object> records;

        public ClientException(HttpStatus status, String code, String message) {
            this(status, code, message, Map.of());
        }

        public ClientException(HttpStatus status, String code, String message, Map<String, ?> records) {
            super(message);
            this.status = status;
            this.code = code;
            this.records = Collections.unmodifiableMap(new LinkedHashMap<>(records));
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getCode() {
            return code;
        }

        /** Extra properties added to the problem body, e.g. {@code constraints}. */
        public Map<String, Object> getRecords() {
            return records;
        }
    }

    /** 400: the request is malformed or not allowed in its current form. */
    public static class BadRequestException extends ClientException {
        public BadRequestException(String message) {
            super(HttpStatus.BAD_REQUEST, "bad-request", message);
        }

        public BadRequestException(String message, Map<String, ?> records) {
            super(HttpStatus.BAD_REQUEST, "bad-request", message, records);
        }
    }

    /** 400: field-level validation failed. {@code constraints} maps field paths to {@code [code, params?]}. */
    public static class ValidationException extends ClientException {
        public ValidationException(Map<String, Object> constraints) {
            super(
                    HttpStatus.BAD_REQUEST,
                    "validation-error",
                    "Validation error. See constraints",
                    Map.of("constraints", constraints));
        }
    }

    /** 401: authentication is missing or invalid. */
    public static class UnauthorizedException extends ClientException {
        public UnauthorizedException() {
            this("Unauthorized");
        }

        public UnauthorizedException(String message) {
            super(HttpStatus.UNAUTHORIZED, "unauthorized", message);
        }
    }

    /** 403: authenticated, but not allowed to do this. */
    public static class ForbiddenException extends ClientException {
        public ForbiddenException() {
            this("Forbidden");
        }

        public ForbiddenException(String message) {
            super(HttpStatus.FORBIDDEN, "forbidden", message);
        }
    }

    /** 404: the resource does not exist, or the caller may not know it exists. */
    public static class NotFoundException extends ClientException {
        public NotFoundException(String message) {
            super(HttpStatus.NOT_FOUND, "not-found", message);
        }
    }

    /** 409: the request conflicts with the current state of the resource. */
    public static class ConflictException extends ClientException {
        public ConflictException(String message) {
            super(HttpStatus.CONFLICT, "conflict", message);
        }

        public ConflictException(String message, Map<String, ?> records) {
            super(HttpStatus.CONFLICT, "conflict", message, records);
        }
    }

    /**
     * 500: an internal failure. The message and records are logged but never shown to the client, who gets a generic
     * {@code server-error} response.
     */
    public static class ServerException extends RuntimeException {
        private final Map<String, Object> records;

        public ServerException(String message) {
            this(message, Map.of());
        }

        public ServerException(String message, Throwable cause) {
            super(message, cause);
            this.records = Map.of();
        }

        public ServerException(String message, Map<String, ?> records) {
            super(message);
            this.records = Collections.unmodifiableMap(new LinkedHashMap<>(records));
        }

        public Map<String, Object> getRecords() {
            return records;
        }
    }
}
