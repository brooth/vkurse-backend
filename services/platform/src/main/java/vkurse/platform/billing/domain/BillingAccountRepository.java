/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.domain;

import java.util.Optional;
import java.util.UUID;

public interface BillingAccountRepository {

    Optional<BillingAccount> findByIdForUpdate(UUID id);

    Optional<BillingAccount> findBySchool(UUID schoolId);

    /** A school's account, locked until the transaction ends. */
    Optional<BillingAccount> findBySchoolForUpdate(UUID schoolId);

    BillingAccount save(BillingAccount account);
}
