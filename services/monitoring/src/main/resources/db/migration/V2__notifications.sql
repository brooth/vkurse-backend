/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

-- Notifications submitted for delivery (libs/core, NotificationService.submit). A job sends the pending ones and
-- retries the failed; sent and failed rows are removed after 30 days.
CREATE TABLE notifications (
    id              UUID        NOT NULL PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL,
    message         JSONB       NOT NULL,
    status          VARCHAR(16) NOT NULL,
    attempts        INT         NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    channel         VARCHAR(16),
    last_error      TEXT
);

CREATE INDEX notifications_due_idx ON notifications (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX notifications_created_at_idx ON notifications (created_at);
