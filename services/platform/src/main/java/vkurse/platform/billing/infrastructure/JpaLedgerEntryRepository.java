/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.billing.domain.LedgerEntry;
import vkurse.platform.billing.domain.LedgerEntryRepository;

@Repository
class JpaLedgerEntryRepository extends JpaAggregateRepository<LedgerEntry> implements LedgerEntryRepository {

    JpaLedgerEntryRepository() {
        super(LedgerEntry.class);
    }

    @Override
    public boolean exists(LedgerEntry.Kind kind, UUID referenceId) {
        return em.createQuery(
                                "SELECT COUNT(e) FROM LedgerEntry e WHERE e.kind = :kind AND e.referenceId = :referenceId",
                                Long.class)
                        .setParameter("kind", kind)
                        .setParameter("referenceId", referenceId)
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<LedgerEntry> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery(
                        "SELECT e FROM LedgerEntry e WHERE e.schoolId = :schoolId ORDER BY e.createdAt DESC, e.id DESC",
                        LedgerEntry.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }
}
