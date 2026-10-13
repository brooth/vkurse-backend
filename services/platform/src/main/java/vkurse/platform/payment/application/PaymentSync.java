/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.payment.domain.CredentialCipher;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantRepository;
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.payment.domain.PaymentRepository;
import vkurse.platform.payment.domain.ProviderPayment;
import vkurse.platform.payment.domain.ProviderRefund;
import vkurse.platform.payment.domain.Refund;
import vkurse.platform.payment.domain.RefundRepository;
import vkurse.platform.payment.domain.WebhookEvent;
import vkurse.platform.payment.domain.WebhookEventRepository;

/**
 * Brings a payment or a refund in line with what the provider says about it. This is the one way an outcome reaches
 * us, whoever asks: a notification, the payer coming back, or the job. The provider is asked outside a transaction;
 * its answer is then applied under a lock on the payment, and the aggregate ignores an answer it already has.
 */
@Component
class PaymentSync {
    private static final Logger log = LoggerFactory.getLogger(PaymentSync.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final MerchantRepository merchants;
    private final WebhookEventRepository inbox;
    private final PaymentGateways gateways;
    private final CredentialCipher cipher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PaymentSync(
            PaymentRepository payments,
            RefundRepository refunds,
            MerchantRepository merchants,
            WebhookEventRepository inbox,
            PaymentGateways gateways,
            CredentialCipher cipher,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.refunds = refunds;
        this.merchants = merchants;
        this.inbox = inbox;
        this.gateways = gateways;
        this.cipher = cipher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Asks the provider about a payment and applies the answer.
     *
     * @param received the notification that made us ask; {@code null} when nobody notified us. It is recorded in the
     *     inbox only if the provider's answer settled the payment, in the same transaction. A notification that changes
     *     nothing (a forged one, an early one, a repeated one) leaves no row, so it can't stand in the way of the real
     *     one
     */
    void refreshPayment(UUID paymentId, @Nullable WebhookEvent received) {
        var known = transaction.execute(status -> payments.findById(paymentId)
                .flatMap(payment ->
                        merchants.findById(payment.getMerchantId()).map(merchant -> new Known(payment, merchant))));
        if (known == null || known.isEmpty()) {
            return;
        }
        var state = ask(known.get().payment(), known.get().merchant());
        var settled = transaction.execute(status -> {
            var payment = payments.findByIdForUpdate(paymentId).orElseThrow();
            if (!payment.isPending()) {
                return null;
            }
            state.ifPresent(answer -> payment.apply(answer, clock.instant()));
            if (received != null && !payment.isPending()) {
                inbox.save(received);
            }
            payment.checked(clock.instant());
            payments.save(payment);
            return payment.isPending() ? null : payment;
        });
        if (settled != null) {
            log.info(
                    "Payment {} of {} for {} {} became {} ({}, provider payment {})",
                    paymentId,
                    settled.getAmount(),
                    settled.getPurpose(),
                    settled.getReferenceId(),
                    settled.getStatus(),
                    received == null ? "found by a check" : "told by a notification",
                    settled.getProviderPaymentId());
        }
    }

    /** Asks the provider about a refund and applies the answer. {@code received} is as in {@link #refreshPayment}. */
    void refreshRefund(UUID refundId, @Nullable WebhookEvent received) {
        var known = transaction.execute(status -> refunds.findById(refundId)
                .flatMap(refund ->
                        merchants.findById(refund.getMerchantId()).map(merchant -> new KnownRefund(refund, merchant))));
        if (known == null || known.isEmpty()) {
            return;
        }
        var state = askRefund(known.get().refund(), known.get().merchant());
        var settled = transaction.execute(status -> {
            var refund = refunds.findById(refundId).orElseThrow();
            if (!refund.isPending() || state.isEmpty()) {
                return null;
            }
            var payment = payments.findByIdForUpdate(refund.getPaymentId()).orElseThrow();
            payment.settle(refund, state.get());
            if (received != null && !refund.isPending()) {
                inbox.save(received);
            }
            refunds.save(refund);
            payments.save(payment);
            return refund.isPending() ? null : refund;
        });
        if (settled != null) {
            log.info(
                    "Refund {} of {} from payment {} became {} ({})",
                    refundId,
                    settled.getAmount(),
                    settled.getPaymentId(),
                    settled.getStatus(),
                    received == null ? "found by a check" : "told by a notification");
        }
    }

    /** The provider's word on the payment; empty if there is nothing to ask, or nobody to ask as. */
    private Optional<ProviderPayment> ask(Payment payment, Merchant merchant) {
        var providerId = payment.getProviderPaymentId();
        if (!payment.isPending() || providerId == null || !merchant.isConnected()) {
            return Optional.empty();
        }
        return gateways.of(merchant.getProvider()).find(merchant.credentials(cipher), providerId);
    }

    private Optional<ProviderRefund> askRefund(Refund refund, Merchant merchant) {
        var providerId = refund.getProviderRefundId();
        if (!refund.isPending() || providerId == null || !merchant.isConnected()) {
            return Optional.empty();
        }
        return gateways.of(merchant.getProvider()).findRefund(merchant.credentials(cipher), providerId);
    }

    private record Known(Payment payment, Merchant merchant) {}

    private record KnownRefund(Refund refund, Merchant merchant) {}
}
