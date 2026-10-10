/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The course module: courses, their sections, and lessons with their content.

-- A deleted course keeps its row (deleted_at) but frees its slug. `search` is kept by the database, for the catalog:
-- the title weighs most, then the summary, then the description.
CREATE TABLE courses (
    id            UUID           NOT NULL PRIMARY KEY,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ,
    version       BIGINT         NOT NULL,
    school_id     UUID           NOT NULL REFERENCES schools (id),
    title         VARCHAR(150)   NOT NULL,
    slug          VARCHAR(80)    NOT NULL,
    summary       VARCHAR(300),
    description   VARCHAR(10000),
    -- The public link of the cover, and the stored file it leads to (no foreign key, as for a school's logo).
    cover         VARCHAR(500),
    cover_file_id UUID,
    status        VARCHAR(16)    NOT NULL,
    published_at  TIMESTAMPTZ,
    deleted_at    TIMESTAMPTZ,
    search        TSVECTOR GENERATED ALWAYS AS (
                      setweight(to_tsvector('russian', title), 'A')
                      || setweight(to_tsvector('russian', coalesce(summary, '')), 'B')
                      || setweight(to_tsvector('russian', coalesce(description, '')), 'C')
                  ) STORED
);

CREATE UNIQUE INDEX courses_school_slug_key ON courses (school_id, slug) WHERE deleted_at IS NULL;
CREATE INDEX courses_published_idx ON courses (published_at DESC) WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
CREATE INDEX courses_search_idx ON courses USING GIN (search);

-- Positions order the sections of a course. They are not unique: a reorder rewrites them one row at a time.
CREATE TABLE course_sections (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    course_id  UUID         NOT NULL REFERENCES courses (id),
    title      VARCHAR(150) NOT NULL,
    position   INT          NOT NULL
);

CREATE INDEX course_sections_course_idx ON course_sections (course_id, position);

-- `content` is the lesson's blocks: { "blocks": [ { "type": "text" | "video" | "image" | "file", "id": …, … } ] }.
CREATE TABLE course_lessons (
    id         UUID         NOT NULL PRIMARY KEY,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ,
    version    BIGINT       NOT NULL,
    course_id  UUID         NOT NULL REFERENCES courses (id),
    section_id UUID         NOT NULL REFERENCES course_sections (id),
    title      VARCHAR(150) NOT NULL,
    position   INT          NOT NULL,
    content    JSONB        NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    preview    BOOLEAN      NOT NULL,
    drip_kind  VARCHAR(16)  NOT NULL,
    drip_days  INT,
    drip_at    TIMESTAMPTZ,
    stop       BOOLEAN      NOT NULL
);

CREATE INDEX course_lessons_course_idx ON course_lessons (course_id);
CREATE INDEX course_lessons_section_idx ON course_lessons (section_id, position);
-- Finds the lessons that show a stored file.
CREATE INDEX course_lessons_content_idx ON course_lessons USING GIN (content jsonb_path_ops);
