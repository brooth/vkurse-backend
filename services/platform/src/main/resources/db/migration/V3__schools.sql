/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The school module: schools, their staff, pending invitations to the staff, and students.

-- A deleted school keeps its row (deleted_at) but frees its slug.
CREATE TABLE schools (
    id          UUID          NOT NULL PRIMARY KEY,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT        NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(40)   NOT NULL,
    description VARCHAR(2000),
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX schools_slug_key ON schools (slug) WHERE deleted_at IS NULL;

-- The staff. The owner of a school is its member with the role OWNER: exactly one.
CREATE TABLE school_members (
    id         UUID        NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT      NOT NULL,
    school_id  UUID        NOT NULL REFERENCES schools (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    role       VARCHAR(16) NOT NULL,
    UNIQUE (school_id, user_id)
);

CREATE UNIQUE INDEX school_members_owner_key ON school_members (school_id) WHERE role = 'OWNER';
CREATE INDEX school_members_user_id_idx ON school_members (user_id);

-- Invitations nobody answered yet: an answered or revoked one is removed. Only a SHA-256 of the link's token is stored.
CREATE TABLE school_invitations (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    school_id  UUID         NOT NULL REFERENCES schools (id),
    email      VARCHAR(320) NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    invited_by UUID         NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    UNIQUE (school_id, email)
);

CREATE INDEX school_invitations_expires_at_idx ON school_invitations (expires_at);

CREATE TABLE school_students (
    id         UUID        NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT      NOT NULL,
    school_id  UUID        NOT NULL REFERENCES schools (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    added_by   UUID        REFERENCES users (id),
    UNIQUE (school_id, user_id)
);

CREATE INDEX school_students_user_id_idx ON school_students (user_id);
