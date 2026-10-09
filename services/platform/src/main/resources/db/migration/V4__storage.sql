/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The storage module: one row per file kept in Supabase Storage. The bytes are there, not here.

-- A file starts PENDING (the client has an upload link) and is READY once confirmed. A deleted file keeps its row
-- (deleted_at) until a job has removed its object from storage; the same job drops uploads that expired unconfirmed.
CREATE TABLE stored_files (
    id                UUID         NOT NULL PRIMARY KEY,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL,
    purpose           VARCHAR(32)  NOT NULL,
    -- NULL for a file of the user who uploaded it (a picture).
    school_id         UUID         REFERENCES schools (id),
    uploaded_by       UUID         NOT NULL REFERENCES users (id),
    name              VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size              BIGINT       NOT NULL,
    path              VARCHAR(255) NOT NULL UNIQUE,
    status            VARCHAR(16)  NOT NULL,
    upload_expires_at TIMESTAMPTZ  NOT NULL,
    deleted_at        TIMESTAMPTZ
);

-- The list of a school's files.
CREATE INDEX stored_files_school_idx ON stored_files (school_id, purpose, created_at DESC)
    WHERE status = 'READY' AND deleted_at IS NULL;
-- The count of a user's unfinished uploads, and the cleanup of the expired ones.
CREATE INDEX stored_files_pending_idx ON stored_files (uploaded_by, upload_expires_at) WHERE status = 'PENDING';
CREATE INDEX stored_files_deleted_idx ON stored_files (deleted_at) WHERE deleted_at IS NOT NULL;

-- A school's logo and a user's uploaded picture: the public link, and the file it leads to. The file ids have no
-- foreign key, so the cleanup of stored_files never waits for these rows (a deleted user keeps its row).
ALTER TABLE schools ADD COLUMN logo VARCHAR(500);
ALTER TABLE schools ADD COLUMN logo_file_id UUID;

-- `picture` is already there: the link a sign-in provider gave, or now the link of an uploaded file.
ALTER TABLE users ADD COLUMN picture_file_id UUID;
