/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vkurse.platform.payment.domain.PaymentRepository;
import vkurse.platform.payment.domain.RefundRepository;

/**
 * Asks providers about the payments and refunds that are still pending, every minute. A notification usually tells us
 * first; this is what makes an outcome reach us when no notification does, for example when a school never set our
 * URL in its shop.
 */
@Component
class PaymentSyncJob {
    private static final Logger log = LoggerFactory.getLogger(PaymentSyncJob.class);

    private static final int BATCH_SIZE = 100;

    /** A refund is left to its notification for this long before the job asks about it. */
    private static final Duration REFUND_GRACE = Duration.ofMinutes(1);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PaymentSync sync;
    private final Clock clock;
    private final TransactionTemplate transaction;

    PaymentSyncJob(
            PaymentRepository payments,
            RefundRepository refunds,
            PaymentSync sync,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.payments = payments;
        this.refunds = refunds;
        this.sync = sync;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "30 * * * * *")
    @SchedulerLock(name = "payment-sync")
    public void syncPending() {
        var now = clock.instant();
        var due = Objects.requireNonNull(transaction.execute(status -> payments.findDueForCheck(now, BATCH_SIZE)));
        for (var paymentId : due) {
            try {
                sync.refreshPayment(paymentId, null);
            } catch (RuntimeException e) {
                log.error("Failed to check payment {}", paymentId, e);
            }
        }
        var pending = Objects.requireNonNull(
                transaction.execute(status -> refunds.findPending(now.minus(REFUND_GRACE), BATCH_SIZE)));
        for (var refundId : pending) {
            try {
                sync.refreshRefund(refundId, null);
            } catch (RuntimeException e) {
                log.error("Failed to check refund {}", refundId, e);
            }
        }
    }
}
