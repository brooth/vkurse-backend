/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The payment module: a school's merchants, payments to them, refunds, and the inbox of provider notifications.

-- A school's account with a payment provider. `credentials` is ciphertext (AES-GCM); it is NULL once the merchant is
-- disconnected, and the row stays because payments point to it.
CREATE TABLE payment_merchants (
    id                UUID         NOT NULL PRIMARY KEY,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL,
    school_id         UUID         NOT NULL REFERENCES schools (id),
    provider          VARCHAR(16)  NOT NULL,
    name              VARCHAR(60)  NOT NULL,
    account_id        VARCHAR(64)  NOT NULL,
    test_mode         BOOLEAN      NOT NULL,
    credentials       TEXT,
    receipts_enabled  BOOLEAN      NOT NULL,
    receipts_vat_code INT,
    status            VARCHAR(16)  NOT NULL
);

CREATE INDEX payment_merchants_school_idx ON payment_merchants (school_id) WHERE status <> 'DISCONNECTED';

-- One payment of a payer to a merchant. `purpose` and `reference_id` are its starter's: what was paid for.
CREATE TABLE payments (
    id                       UUID          NOT NULL PRIMARY KEY,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ,
    version                  BIGINT        NOT NULL,
    merchant_id              UUID          NOT NULL REFERENCES payment_merchants (id),
    school_id                UUID          NOT NULL REFERENCES schools (id),
    payer_id                 UUID          NOT NULL REFERENCES users (id),
    amount_value             NUMERIC(19,2) NOT NULL,
    amount_currency          VARCHAR(3)    NOT NULL,
    description              VARCHAR(128)  NOT NULL,
    purpose                  VARCHAR(32)   NOT NULL,
    reference_id             UUID          NOT NULL,
    status                   VARCHAR(16)   NOT NULL,
    provider_payment_id      VARCHAR(64),
    confirmation_url         VARCHAR(1000),
    return_url               VARCHAR(500)  NOT NULL,
    paid_at                  TIMESTAMPTZ,
    canceled_at              TIMESTAMPTZ,
    cancellation_reason      VARCHAR(100),
    refunded_amount_value    NUMERIC(19,2) NOT NULL,
    refunded_amount_currency VARCHAR(3)    NOT NULL,
    next_check_at            TIMESTAMPTZ
);

-- A provider's id names one payment of a merchant.
CREATE UNIQUE INDEX payments_provider_id_key ON payments (merchant_id, provider_payment_id);
CREATE INDEX payments_school_idx ON payments (school_id, created_at DESC);
CREATE INDEX payments_reference_idx ON payments (purpose, reference_id);
-- The job that asks providers about pending payments.
CREATE INDEX payments_next_check_idx ON payments (next_check_at) WHERE next_check_at IS NOT NULL;

CREATE TABLE payment_refunds (
    id                 UUID          NOT NULL PRIMARY KEY,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ,
    version            BIGINT        NOT NULL,
    payment_id         UUID          NOT NULL REFERENCES payments (id),
    merchant_id        UUID          NOT NULL REFERENCES payment_merchants (id),
    amount_value       NUMERIC(19,2) NOT NULL,
    amount_currency    VARCHAR(3)    NOT NULL,
    reason             VARCHAR(250),
    requested_by       UUID          NOT NULL REFERENCES users (id),
    status             VARCHAR(16)   NOT NULL,
    provider_refund_id VARCHAR(64)
);

CREATE INDEX payment_refunds_payment_idx ON payment_refunds (payment_id);
CREATE UNIQUE INDEX payment_refunds_provider_id_key ON payment_refunds (merchant_id, provider_refund_id);
CREATE INDEX payment_refunds_pending_idx ON payment_refunds (created_at) WHERE status = 'PENDING';

-- The inbox: a notification that is delivered again finds its row here and is not acted on twice.
CREATE TABLE payment_webhook_events (
    id          UUID         NOT NULL PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ,
    version     BIGINT       NOT NULL,
    provider    VARCHAR(16)  NOT NULL,
    merchant_id UUID         NOT NULL REFERENCES payment_merchants (id),
    event       VARCHAR(64)  NOT NULL,
    object_id   VARCHAR(64)  NOT NULL,
    UNIQUE (provider, merchant_id, event, object_id)
);
