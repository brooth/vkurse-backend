/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The commerce module: a course's offers, a school's promo codes, and orders.

-- One way to buy a course. An archived offer keeps its row (archived_at), because orders point to it.
CREATE TABLE offers (
    id                    UUID          NOT NULL PRIMARY KEY,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ,
    version               BIGINT        NOT NULL,
    school_id             UUID          NOT NULL REFERENCES schools (id),
    course_id             UUID          NOT NULL REFERENCES courses (id),
    name                  VARCHAR(100)  NOT NULL,
    description           VARCHAR(1000),
    price_amount_value    NUMERIC(19,2) NOT NULL,
    price_amount_currency VARCHAR(3)    NOT NULL,
    access_days           INT,
    active                BOOLEAN       NOT NULL,
    position              INT           NOT NULL,
    archived_at           TIMESTAMPTZ
);

CREATE INDEX offers_course_idx ON offers (course_id, position) WHERE archived_at IS NULL;

-- `scope` is the offers the code is for: { "offerIds": [ … ] }, empty for every offer of the school. `used_count` is
-- the orders that use the code now: placed with it and paid, or still waiting for payment.
CREATE TABLE promo_codes (
    id               UUID          NOT NULL PRIMARY KEY,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ,
    version          BIGINT        NOT NULL,
    school_id        UUID          NOT NULL REFERENCES schools (id),
    code             VARCHAR(32)   NOT NULL,
    discount_kind    VARCHAR(16)   NOT NULL,
    discount_percent INT,
    discount_amount  NUMERIC(19,2),
    valid_until      TIMESTAMPTZ,
    max_uses         INT,
    used_count       INT           NOT NULL,
    scope            JSONB         NOT NULL,
    active           BOOLEAN       NOT NULL,
    archived_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX promo_codes_school_code_key ON promo_codes (school_id, code) WHERE archived_at IS NULL;

-- An order keeps what was bought as it was when ordered: the names, the price, the discount and the total.
CREATE TABLE orders (
    id                       UUID          NOT NULL PRIMARY KEY,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ,
    version                  BIGINT        NOT NULL,
    school_id                UUID          NOT NULL REFERENCES schools (id),
    buyer_id                 UUID          NOT NULL REFERENCES users (id),
    offer_id                 UUID          NOT NULL REFERENCES offers (id),
    course_id                UUID          NOT NULL REFERENCES courses (id),
    offer_name               VARCHAR(100)  NOT NULL,
    course_title             VARCHAR(150)  NOT NULL,
    access_days              INT,
    price_amount_value       NUMERIC(19,2) NOT NULL,
    price_amount_currency    VARCHAR(3)    NOT NULL,
    discount_amount_value    NUMERIC(19,2) NOT NULL,
    discount_amount_currency VARCHAR(3)    NOT NULL,
    total_amount_value       NUMERIC(19,2) NOT NULL,
    total_amount_currency    VARCHAR(3)    NOT NULL,
    promo_code_id            UUID          REFERENCES promo_codes (id),
    promo_code               VARCHAR(32),
    status                   VARCHAR(16)   NOT NULL,
    -- The payment last started for the order (no foreign key: the payment module owns that table).
    payment_id               UUID,
    paid_at                  TIMESTAMPTZ,
    expires_at               TIMESTAMPTZ   NOT NULL
);

-- A buyer has a course once.
CREATE UNIQUE INDEX orders_paid_key ON orders (buyer_id, course_id) WHERE status = 'PAID';
CREATE INDEX orders_buyer_idx ON orders (buyer_id, created_at DESC);
CREATE INDEX orders_school_idx ON orders (school_id, created_at DESC);
-- The job that expires unpaid orders.
CREATE INDEX orders_pending_idx ON orders (expires_at) WHERE status = 'PENDING';
