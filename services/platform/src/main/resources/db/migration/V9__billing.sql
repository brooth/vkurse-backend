/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- The billing module: a school's balance at Vkurse, its ledger, daily storage usage, and the price of storage.

-- Vkurse's own shop is the one merchant without a school. Schools pay Vkurse through it.
ALTER TABLE payment_merchants ALTER COLUMN school_id DROP NOT NULL;
CREATE UNIQUE INDEX payment_merchants_platform_key ON payment_merchants ((1)) WHERE school_id IS NULL;

-- A school's money at Vkurse. The balance is below zero when the school owes; debt_since says since when. The
-- *_notified_at columns say when the owner was told about each state, and are cleared when the state ends.
CREATE TABLE billing_accounts (
    id                      UUID          NOT NULL PRIMARY KEY,
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ,
    version                 BIGINT        NOT NULL,
    school_id               UUID          NOT NULL REFERENCES schools (id),
    balance_amount_value    NUMERIC(19,2) NOT NULL,
    balance_amount_currency VARCHAR(3)    NOT NULL,
    debt_since              TIMESTAMPTZ,
    low_balance_notified_at TIMESTAMPTZ,
    debt_notified_at        TIMESTAMPTZ,
    suspension_notified_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX billing_accounts_school_key ON billing_accounts (school_id);

-- One change of a balance, written in the transaction that changed it. `amount` is above zero for money in and below
-- for money out. `reference_id` is what caused it: a payment (TOP_UP), a usage row (STORAGE_CHARGE), a refund (REFUND).
CREATE TABLE billing_ledger (
    id                            UUID          NOT NULL PRIMARY KEY,
    created_at                    TIMESTAMPTZ   NOT NULL,
    updated_at                    TIMESTAMPTZ,
    version                       BIGINT        NOT NULL,
    account_id                    UUID          NOT NULL REFERENCES billing_accounts (id),
    school_id                     UUID          NOT NULL REFERENCES schools (id),
    kind                          VARCHAR(16)   NOT NULL,
    amount_value                  NUMERIC(19,2) NOT NULL,
    amount_currency               VARCHAR(3)    NOT NULL,
    balance_after_amount_value    NUMERIC(19,2) NOT NULL,
    balance_after_amount_currency VARCHAR(3)    NOT NULL,
    reference_id                  UUID          NOT NULL
);

-- A payment is credited once, a day is charged once, a refund is taken back once.
CREATE UNIQUE INDEX billing_ledger_reference_key ON billing_ledger (kind, reference_id);
CREATE INDEX billing_ledger_school_idx ON billing_ledger (school_id, created_at DESC);

-- What a school stored on a day (Moscow time) and what the day cost, at the price of that day.
CREATE TABLE billing_storage_usage (
    id                     UUID          NOT NULL PRIMARY KEY,
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL,
    school_id              UUID          NOT NULL REFERENCES schools (id),
    day                    DATE          NOT NULL,
    bytes                  BIGINT        NOT NULL,
    price_amount_value     NUMERIC(19,2) NOT NULL,
    price_amount_currency  VARCHAR(3)    NOT NULL,
    charge_amount_value    NUMERIC(19,2) NOT NULL,
    charge_amount_currency VARCHAR(3)    NOT NULL
);

CREATE UNIQUE INDEX billing_storage_usage_key ON billing_storage_usage (school_id, day);
CREATE INDEX billing_storage_usage_day_idx ON billing_storage_usage (day);

-- The price of one GB (1024^3 bytes) kept for a month, from a day on. The latest row that has started is in force.
CREATE TABLE billing_storage_prices (
    id              UUID          NOT NULL PRIMARY KEY,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ,
    version         BIGINT        NOT NULL,
    amount_value    NUMERIC(19,2) NOT NULL,
    amount_currency VARCHAR(3)    NOT NULL,
    effective_from  DATE          NOT NULL
);

CREATE UNIQUE INDEX billing_storage_prices_from_key ON billing_storage_prices (effective_from);

-- The first price. A placeholder until the real one is decided; an admin changes it by adding a row.
INSERT INTO billing_storage_prices (id, created_at, version, amount_value, amount_currency, effective_from)
VALUES ('01900000-0000-7000-8000-000000000001', now(), 0, 5.00, 'RUB', DATE '2026-01-01');
