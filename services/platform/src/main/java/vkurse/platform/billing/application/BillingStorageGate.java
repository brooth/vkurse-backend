/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vkurse.platform.billing.domain.BillingAccountRepository;
import vkurse.platform.storage.domain.SchoolStorageGate;

/**
 * Closes storage to a school that owes Vkurse: uploads at once, reading after the grace period. A school with no
 * account yet owes nothing.
 */
@Component
class BillingStorageGate implements SchoolStorageGate {
    private final BillingAccountRepository accounts;
    private final Clock clock;

    BillingStorageGate(BillingAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    public void ensureCanUpload(UUID schoolId) {
        accounts.findBySchool(schoolId).ifPresent(account -> account.ensureCanUpload());
    }

    @Override
    public void ensureCanRead(UUID schoolId) {
        accounts.findBySchool(schoolId).ifPresent(account -> account.ensureCanRead(clock.instant()));
    }
}
