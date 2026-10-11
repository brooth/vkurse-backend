/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The chat module: a chat per course, its messages (banners among them), votes, and how far each person has read.

CREATE TABLE chats (
    id          UUID        NOT NULL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL,
    school_id   UUID        NOT NULL REFERENCES schools (id),
    course_id   UUID        NOT NULL REFERENCES courses (id)
);

CREATE UNIQUE INDEX chats_course_key ON chats (course_id);
CREATE INDEX chats_school_idx ON chats (school_id);

-- Ids are time-ordered (UUIDv7), so (chat_id, id) is the feed. `attachments` is { "fileIds": [ … ] }: stored files
-- of the purpose CHAT_FILE. `banner` is set for a banner. A deleted message keeps its row, emptied (deleted_at).
CREATE TABLE chat_messages (
    id           UUID          NOT NULL PRIMARY KEY,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ,
    version      BIGINT        NOT NULL,
    chat_id      UUID          NOT NULL REFERENCES chats (id),
    school_id    UUID          NOT NULL REFERENCES schools (id),
    author_id    UUID          NOT NULL REFERENCES users (id),
    text         VARCHAR(4000) NOT NULL,
    attachments  JSONB         NOT NULL,
    reply_to_id  UUID          REFERENCES chat_messages (id),
    banner       VARCHAR(16),
    pinned       BOOLEAN       NOT NULL,
    pinned_until TIMESTAMPTZ,
    edited_at    TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX chat_messages_feed_idx ON chat_messages (chat_id, id);
CREATE INDEX chat_messages_pinned_idx ON chat_messages (chat_id) WHERE pinned;
-- The flood check.
CREATE INDEX chat_messages_author_idx ON chat_messages (chat_id, author_id, created_at);

CREATE TABLE chat_message_votes (
    id          UUID        NOT NULL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL,
    message_id  UUID        NOT NULL REFERENCES chat_messages (id),
    chat_id     UUID        NOT NULL REFERENCES chats (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    vote        INT         NOT NULL
);

CREATE UNIQUE INDEX chat_message_votes_key ON chat_message_votes (message_id, user_id);

-- The last message a person has read in a chat.
CREATE TABLE chat_reads (
    id                   UUID        NOT NULL PRIMARY KEY,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ,
    version              BIGINT      NOT NULL,
    chat_id              UUID        NOT NULL REFERENCES chats (id),
    user_id              UUID        NOT NULL REFERENCES users (id),
    last_read_message_id UUID        NOT NULL
);

CREATE UNIQUE INDEX chat_reads_key ON chat_reads (chat_id, user_id);

-- A chat for every course that exists already. New courses get theirs when they are made.
INSERT INTO chats (id, created_at, version, school_id, course_id)
SELECT gen_random_uuid(), now(), 0, c.school_id, c.id FROM courses c;
