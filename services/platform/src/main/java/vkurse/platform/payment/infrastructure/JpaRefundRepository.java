/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.payment.domain.Refund;
import vkurse.platform.payment.domain.RefundRepository;

@Repository
class JpaRefundRepository extends JpaAggregateRepository<Refund> implements RefundRepository {

    JpaRefundRepository() {
        super(Refund.class);
    }

    @Override
    public Optional<Refund> findByProviderId(UUID merchantId, String providerRefundId) {
        return em
                .createQuery(
                        "SELECT r FROM Refund r WHERE r.merchantId = :merchantId AND r.providerRefundId = :providerId",
                        Refund.class)
                .setParameter("merchantId", merchantId)
                .setParameter("providerId", providerRefundId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Refund> findByPayment(UUID paymentId) {
        return em.createQuery(
                        "SELECT r FROM Refund r WHERE r.paymentId = :paymentId ORDER BY r.createdAt, r.id",
                        Refund.class)
                .setParameter("paymentId", paymentId)
                .getResultList();
    }

    @Override
    public List<UUID> findPending(Instant before, int limit) {
        return em.createQuery("""
                        SELECT r.id FROM Refund r
                        WHERE r.status = :pending AND r.providerRefundId IS NOT NULL AND r.createdAt < :before
                        ORDER BY r.createdAt
                        """, UUID.class)
                .setParameter("pending", Refund.Status.PENDING)
                .setParameter("before", before)
                .setMaxResults(limit)
                .getResultList();
    }
}
