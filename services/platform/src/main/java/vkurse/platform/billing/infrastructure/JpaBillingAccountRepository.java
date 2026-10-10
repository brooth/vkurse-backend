/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.billing.domain.BillingAccount;
import vkurse.platform.billing.domain.BillingAccountRepository;

@Repository
class JpaBillingAccountRepository extends JpaAggregateRepository<BillingAccount> implements BillingAccountRepository {

    JpaBillingAccountRepository() {
        super(BillingAccount.class);
    }

    @Override
    public Optional<BillingAccount> findBySchool(UUID schoolId) {
        return find(schoolId, LockModeType.NONE);
    }

    @Override
    public Optional<BillingAccount> findBySchoolForUpdate(UUID schoolId) {
        return find(schoolId, LockModeType.PESSIMISTIC_WRITE);
    }

    private Optional<BillingAccount> find(UUID schoolId, LockModeType lock) {
        return em
                .createQuery("SELECT a FROM BillingAccount a WHERE a.schoolId = :schoolId", BillingAccount.class)
                .setParameter("schoolId", schoolId)
                .setLockMode(lock)
                .getResultList()
                .stream()
                .findFirst();
    }
}
