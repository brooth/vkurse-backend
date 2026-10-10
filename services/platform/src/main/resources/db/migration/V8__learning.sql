/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The learning module: who has which course, completed lessons, homework answers and quiz attempts.

-- A buyer may buy a course again once the access of an earlier paid order has run out, so two paid orders for one
-- course are possible now. "Already bought" is the order's own rule (Order.givesCourse).
DROP INDEX orders_paid_key;
CREATE INDEX orders_paid_idx ON orders (buyer_id, course_id) WHERE status = 'PAID';

-- A student has a course. One row per student and course: revoked_at and expires_at say whether the access lasts,
-- and giving the course again reuses the row, so the progress stays.
CREATE TABLE enrollments (
    id          UUID        NOT NULL PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL,
    school_id   UUID        NOT NULL REFERENCES schools (id),
    course_id   UUID        NOT NULL REFERENCES courses (id),
    student_id  UUID        NOT NULL REFERENCES users (id),
    source      VARCHAR(16) NOT NULL,
    -- The paid order that last gave the access (no foreign key: the commerce module owns that table).
    order_id    UUID,
    granted_by  UUID        REFERENCES users (id),
    started_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX enrollments_student_course_key ON enrollments (student_id, course_id);
CREATE INDEX enrollments_course_idx ON enrollments (course_id, created_at DESC);
CREATE INDEX enrollments_school_student_idx ON enrollments (school_id, student_id);

-- lesson_id has no foreign key in these tables: a removed lesson is a removed row, and what students did in it stays.
CREATE TABLE lesson_completions (
    id            UUID        NOT NULL PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ,
    version       BIGINT      NOT NULL,
    enrollment_id UUID        NOT NULL REFERENCES enrollments (id),
    course_id     UUID        NOT NULL REFERENCES courses (id),
    lesson_id     UUID        NOT NULL,
    student_id    UUID        NOT NULL REFERENCES users (id)
);

CREATE UNIQUE INDEX lesson_completions_key ON lesson_completions (enrollment_id, lesson_id);

-- One answer to a lesson's homework. `files` is { "fileIds": [ … ] }: stored files of the purpose HOMEWORK_FILE.
CREATE TABLE homework_submissions (
    id            UUID          NOT NULL PRIMARY KEY,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ,
    version       BIGINT        NOT NULL,
    enrollment_id UUID          NOT NULL REFERENCES enrollments (id),
    school_id     UUID          NOT NULL REFERENCES schools (id),
    course_id     UUID          NOT NULL REFERENCES courses (id),
    lesson_id     UUID          NOT NULL,
    student_id    UUID          NOT NULL REFERENCES users (id),
    text          TEXT          NOT NULL,
    files         JSONB         NOT NULL,
    status        VARCHAR(16)   NOT NULL,
    reviewer_id   UUID          REFERENCES users (id),
    comment       VARCHAR(5000),
    reviewed_at   TIMESTAMPTZ
);

CREATE INDEX homework_submissions_lesson_idx ON homework_submissions (enrollment_id, lesson_id, created_at DESC);
-- The review queue.
CREATE INDEX homework_submissions_school_idx ON homework_submissions (school_id, status, created_at);

-- One graded try at a lesson's quiz. `answers` is { "chosen": { questionId: [ optionId ] }, "wrongQuestionIds": [ … ] }.
CREATE TABLE quiz_attempts (
    id             UUID        NOT NULL PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ,
    version        BIGINT      NOT NULL,
    enrollment_id  UUID        NOT NULL REFERENCES enrollments (id),
    course_id      UUID        NOT NULL REFERENCES courses (id),
    lesson_id      UUID        NOT NULL,
    student_id     UUID        NOT NULL REFERENCES users (id),
    answers        JSONB       NOT NULL,
    right_count    INT         NOT NULL,
    question_count INT         NOT NULL,
    passed         BOOLEAN     NOT NULL
);

CREATE INDEX quiz_attempts_lesson_idx ON quiz_attempts (enrollment_id, lesson_id, created_at DESC);
