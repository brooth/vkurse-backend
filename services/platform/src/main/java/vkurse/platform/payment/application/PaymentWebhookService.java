/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.core.domain.AggregateRoot;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantRepository;
import vkurse.platform.payment.domain.PaymentProvider;
import vkurse.platform.payment.domain.PaymentRepository;
import vkurse.platform.payment.domain.ProviderNotification;
import vkurse.platform.payment.domain.RefundRepository;
import vkurse.platform.payment.domain.WebhookEvent;

/**
 * Notifications from payment providers. Anyone can send one, so its body is never believed: it only names a payment
 * or a refund, and we ask the provider about that with the merchant's own credentials. A notification about
 * something that is not ours, or one we can't read, is ignored quietly.
 *
 * <p>Each outcome is acted on once. The provider's answer is applied under a lock on the payment, and a payment that
 * is settled ignores every later answer; the notification that settled it is recorded in the inbox in the same
 * transaction. A notification that is delivered again therefore changes nothing.
 */
@Service
public class PaymentWebhookService {
    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookService.class);

    private final MerchantRepository merchants;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentGateways gateways;
    private final PaymentSync sync;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PaymentWebhookService(
            MerchantRepository merchants,
            PaymentRepository payments,
            RefundRepository refunds,
            PaymentGateways gateways,
            PaymentSync sync,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.merchants = merchants;
        this.payments = payments;
        this.refunds = refunds;
        this.gateways = gateways;
        this.sync = sync;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Handles a notification sent to a merchant's URL. It returns normally whenever there is nothing more to do, and
     * throws only if the provider should send the notification again.
     */
    public void handle(PaymentProvider provider, UUID merchantId, String body) {
        var notification = gateways.of(provider).parse(body).orElse(null);
        if (notification == null || notification.kind() == ProviderNotification.Kind.OTHER) {
            return;
        }
        var target = transaction.execute(status -> merchants
                .findById(merchantId)
                .filter(merchant -> merchant.getProvider() == provider && merchant.isConnected())
                .map(merchant -> new Target(merchant, ours(merchant, notification)))
                .orElse(null));
        if (target == null || target.objectId() == null) {
            log.info("Ignored a {} notification {} for merchant {}", provider, notification.event(), merchantId);
            return;
        }
        var received = WebhookEvent.received(target.merchant(), notification, clock.instant());
        try {
            if (notification.kind() == ProviderNotification.Kind.PAYMENT) {
                sync.refreshPayment(target.objectId(), received);
            } else {
                sync.refreshRefund(target.objectId(), received);
            }
        } catch (DataIntegrityViolationException e) {
            // Two deliveries of one notification raced, and the other one recorded it: nothing more to do.
            log.info("A {} notification {} for merchant {} came again", provider, notification.event(), merchantId);
        }
    }

    /** Our id of the payment or refund of that merchant the notification names; {@code null} if we have none. */
    private @Nullable UUID ours(Merchant merchant, ProviderNotification notification) {
        return switch (notification.kind()) {
            case PAYMENT ->
                payments.findByProviderId(merchant.getId(), notification.objectId())
                        .map(AggregateRoot::getId)
                        .orElse(null);
            case REFUND ->
                refunds.findByProviderId(merchant.getId(), notification.objectId())
                        .map(AggregateRoot::getId)
                        .orElse(null);
            case OTHER -> null;
        };
    }

    private record Target(Merchant merchant, @Nullable UUID objectId) {}
}
