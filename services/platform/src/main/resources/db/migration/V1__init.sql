/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- Baseline of the platform service. Flyway runs with default schema `platform`, so every table lands there.
-- Every aggregate table has the AggregateRoot columns: id (UUIDv7, assigned by the service), created_at,
-- updated_at, version.

-- Cluster-wide locks for @SchedulerLock jobs and LockService (ShedLock).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Spring Modulith event publication registry (JDBC, schema v2): events are stored in the publishing
-- transaction and re-delivered until every listener completes.
CREATE TABLE event_publication (
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- A deleted account keeps its row (deleted_at) but frees its email.
CREATE TABLE users (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(320) NOT NULL,
    picture    TEXT,
    locale     VARCHAR(8)   NOT NULL,
    timezone   VARCHAR(64)  NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX users_email_key ON users (email) WHERE deleted_at IS NULL;

-- A signed-in device. It ends at expires_at, or earlier when revoked (sign-out, replayed refresh token).
CREATE TABLE auth_sessions (
    id                  UUID        NOT NULL PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL,
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider            VARCHAR(16) NOT NULL,
    user_agent          TEXT,
    last_activity_at    TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    refresh_generation  INT         NOT NULL,
    exchange_code_hash  VARCHAR(64) UNIQUE,
    exchange_expires_at TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ
);

CREATE INDEX auth_sessions_user_id_idx ON auth_sessions (user_id);
CREATE INDEX auth_sessions_expires_at_idx ON auth_sessions (expires_at);

-- Google/Apple accounts, found by the provider's stable subject id.
CREATE TABLE auth_linked_accounts (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   VARCHAR(16)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(320),
    UNIQUE (provider, subject)
);

CREATE INDEX auth_linked_accounts_user_id_idx ON auth_linked_accounts (user_id);

-- Email sign-in codes. Only an HMAC of the code is stored.
CREATE TABLE auth_otp_codes (
    id            UUID         NOT NULL PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL,
    email         VARCHAR(320) NOT NULL,
    code_hash     VARCHAR(64)  NOT NULL,
    attempts_left INT          NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX auth_otp_codes_email_idx ON auth_otp_codes (email, created_at);
CREATE INDEX auth_otp_codes_expires_at_idx ON auth_otp_codes (expires_at);

-- OAuth sign-ins sent to the provider and not back yet (instead of the HTTP session).
CREATE TABLE auth_oauth_requests (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    state      VARCHAR(255) NOT NULL UNIQUE,
    request    JSONB        NOT NULL,
    callback   TEXT         NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX auth_oauth_requests_expires_at_idx ON auth_oauth_requests (expires_at);
