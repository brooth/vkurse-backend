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
import vkurse.platform.payment.domain.Payment;
import vkurse.platform.payment.domain.PaymentRepository;

@Repository
class JpaPaymentRepository extends JpaAggregateRepository<Payment> implements PaymentRepository {

    JpaPaymentRepository() {
        super(Payment.class);
    }

    @Override
    public Optional<Payment> findByProviderId(UUID merchantId, String providerPaymentId) {
        return em
                .createQuery(
                        "SELECT p FROM Payment p WHERE p.merchantId = :merchantId AND p.providerPaymentId = :providerId",
                        Payment.class)
                .setParameter("merchantId", merchantId)
                .setParameter("providerId", providerPaymentId)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<Payment> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery(
                        "SELECT p FROM Payment p WHERE p.schoolId = :schoolId ORDER BY p.createdAt DESC, p.id DESC",
                        Payment.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<UUID> findDueForCheck(Instant now, int limit) {
        return em.createQuery(
                        "SELECT p.id FROM Payment p WHERE p.nextCheckAt <= :now ORDER BY p.nextCheckAt", UUID.class)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }
}
