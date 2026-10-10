/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

public interface WebhookEventRepository {

    /**
     * Records a notification that settled something. One that was recorded before breaks the unique index: the write fails with
     * Spring's {@code DataIntegrityViolationException}, and the transaction it was in can only roll back.
     */
    WebhookEvent save(WebhookEvent event);
}
