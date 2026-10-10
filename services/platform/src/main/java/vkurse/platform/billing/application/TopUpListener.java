/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import vkurse.platform.billing.domain.BillingAccountRepository;
import vkurse.platform.billing.domain.LedgerEntry;
import vkurse.platform.billing.domain.LedgerEntryRepository;
import vkurse.platform.payment.application.PaymentService;
import vkurse.platform.payment.domain.PaymentRefunded;
import vkurse.platform.payment.domain.PaymentSucceeded;

/**
 * Moves a school's balance when its top-up is paid or given back. Each event is handled after the payment's change is
 * committed, in a transaction of its own, and delivered again if handling fails; the ledger says whether it was
 * handled already, so the balance moves once.
 */
@Component
class TopUpListener {
    private static final Logger log = LoggerFactory.getLogger(TopUpListener.class);

    private final BillingAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final PaymentService payments;
    private final Clock clock;

    TopUpListener(
            BillingAccountRepository accounts, LedgerEntryRepository ledger, PaymentService payments, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.payments = payments;
        this.clock = clock;
    }

    @ApplicationModuleListener
    public void on(PaymentSucceeded event) {
        if (!BillingService.TOP_UP_PURPOSE.equals(event.purpose())) {
            return;
        }
        // How much was paid is what our own payment row says.
        var payment = payments.find(event.paymentId()).orElse(null);
        var account = accounts.findByIdForUpdate(event.referenceId()).orElse(null);
        if (payment == null || account == null) {
            log.error(
                    "Top-up payment {} succeeded for account {}, which can't be found",
                    event.paymentId(),
                    event.referenceId());
            return;
        }
        if (ledger.exists(LedgerEntry.Kind.TOP_UP, payment.getId())) {
            return;
        }
        ledger.save(account.topUp(payment.getAmount(), payment.getId(), clock.instant()));
        accounts.save(account);
    }

    @ApplicationModuleListener
    public void on(PaymentRefunded event) {
        if (!BillingService.TOP_UP_PURPOSE.equals(event.purpose())) {
            return;
        }
        var account = accounts.findByIdForUpdate(event.referenceId()).orElse(null);
        if (account == null) {
            log.error(
                    "Top-up payment {} was refunded for account {}, which can't be found",
                    event.paymentId(),
                    event.referenceId());
            return;
        }
        if (ledger.exists(LedgerEntry.Kind.REFUND, event.refundId())) {
            return;
        }
        ledger.save(account.takeBack(event.amount(), event.refundId(), clock.instant()));
        accounts.save(account);
    }
}
