/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.billing.domain.StoragePrice;
import vkurse.platform.billing.domain.StoragePriceRepository;

@Repository
class JpaStoragePriceRepository extends JpaAggregateRepository<StoragePrice> implements StoragePriceRepository {

    JpaStoragePriceRepository() {
        super(StoragePrice.class);
    }

    @Override
    public Optional<StoragePrice> findInForce(LocalDate day) {
        return em
                .createQuery(
                        "SELECT p FROM StoragePrice p WHERE p.effectiveFrom <= :day ORDER BY p.effectiveFrom DESC, p.id DESC",
                        StoragePrice.class)
                .setParameter("day", day)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }
}
