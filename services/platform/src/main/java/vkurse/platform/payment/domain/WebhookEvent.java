/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import vkurse.core.domain.AggregateRoot;

/**
 * The inbox of provider notifications: one row for each notification that settled a payment or a refund, written in
 * the same transaction as that change. A notification that changed nothing is not recorded. The provider, merchant,
 * event and object are unique together, which stops two deliveries of one notification that race each other.
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter
public class WebhookEvent extends AggregateRoot {
    @Enumerated(EnumType.STRING)
    private PaymentProvider provider;

    private UUID merchantId;

    /** The provider's name of what happened. */
    private String event;

    /** The provider's id of the payment or refund it happened to. */
    private String objectId;

    protected WebhookEvent() {}

    private WebhookEvent(PaymentProvider provider, UUID merchantId, ProviderNotification notification, Instant now) {
        super(now);
        this.provider = provider;
        this.merchantId = merchantId;
        this.event = notification.event();
        this.objectId = notification.objectId();
    }

    public static WebhookEvent received(Merchant merchant, ProviderNotification notification, Instant now) {
        return new WebhookEvent(merchant.getProvider(), merchant.getId(), notification, now);
    }
}
