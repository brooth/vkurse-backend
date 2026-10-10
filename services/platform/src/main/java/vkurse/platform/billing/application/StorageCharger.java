/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.platform.billing.domain.BillingAccount;
import vkurse.platform.billing.domain.BillingAccountRepository;
import vkurse.platform.billing.domain.LedgerEntryRepository;
import vkurse.platform.billing.domain.StoragePrice;
import vkurse.platform.billing.domain.StorageUsage;
import vkurse.platform.billing.domain.StorageUsageRepository;
import vkurse.platform.school.application.SchoolService;

/** Charges one school for one day of storage. Call it inside a transaction of its own. */
@Component
class StorageCharger {
    private final BillingAccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final StorageUsageRepository usages;
    private final SchoolService schools;
    private final BillingNotifier notifier;
    private final Clock clock;

    StorageCharger(
            BillingAccountRepository accounts,
            LedgerEntryRepository ledger,
            StorageUsageRepository usages,
            SchoolService schools,
            BillingNotifier notifier,
            Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.usages = usages;
        this.schools = schools;
        this.notifier = notifier;
        this.clock = clock;
    }

    /**
     * Records what a school stores for a day and takes the day's cost from its balance, once: a day that is recorded
     * already is left alone. A deleted school is not charged.
     *
     * @return whether the day was recorded now
     */
    boolean charge(UUID schoolId, long bytes, LocalDate day, StoragePrice price) {
        if (schools.find(schoolId).isEmpty()) {
            return false;
        }
        var now = clock.instant();
        // The account's lock makes the day's check and its charge one step.
        var account = accounts.findBySchoolForUpdate(schoolId)
                .orElseGet(() -> accounts.save(BillingAccount.open(schoolId, now)));
        if (usages.exists(schoolId, day)) {
            return false;
        }
        var usage = usages.save(StorageUsage.measure(schoolId, day, bytes, price, now));
        if (usage.isCharged()) {
            ledger.save(account.charge(usage, now));
        }
        var notice = account.noticeDue(usage.getChargeAmount(), now);
        accounts.save(account);
        notice.ifPresent(due -> notifier.tell(account, due));
        return true;
    }
}
