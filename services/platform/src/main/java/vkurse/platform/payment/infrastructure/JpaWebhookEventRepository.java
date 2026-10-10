/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.infrastructure;

import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.payment.domain.WebhookEvent;
import vkurse.platform.payment.domain.WebhookEventRepository;

@Repository
class JpaWebhookEventRepository extends JpaAggregateRepository<WebhookEvent> implements WebhookEventRepository {

    JpaWebhookEventRepository() {
        super(WebhookEvent.class);
    }
}
