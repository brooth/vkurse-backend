/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.infrastructure;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.billing.domain.StorageUsage;
import vkurse.platform.billing.domain.StorageUsageRepository;

@Repository
class JpaStorageUsageRepository extends JpaAggregateRepository<StorageUsage> implements StorageUsageRepository {

    JpaStorageUsageRepository() {
        super(StorageUsage.class);
    }

    @Override
    public Set<UUID> findMeasuredSchools(LocalDate day) {
        return new HashSet<>(em.createQuery("SELECT u.schoolId FROM StorageUsage u WHERE u.day = :day", UUID.class)
                .setParameter("day", day)
                .getResultList());
    }

    @Override
    public boolean exists(UUID schoolId, LocalDate day) {
        return em.createQuery(
                                "SELECT COUNT(u) FROM StorageUsage u WHERE u.schoolId = :schoolId AND u.day = :day",
                                Long.class)
                        .setParameter("schoolId", schoolId)
                        .setParameter("day", day)
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<StorageUsage> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery(
                        "SELECT u FROM StorageUsage u WHERE u.schoolId = :schoolId ORDER BY u.day DESC",
                        StorageUsage.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }
}
