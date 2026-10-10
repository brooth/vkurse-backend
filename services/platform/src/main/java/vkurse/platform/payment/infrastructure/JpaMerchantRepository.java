/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.payment.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.payment.domain.Merchant;
import vkurse.platform.payment.domain.MerchantRepository;

@Repository
class JpaMerchantRepository extends JpaAggregateRepository<Merchant> implements MerchantRepository {

    JpaMerchantRepository() {
        super(Merchant.class);
    }

    @Override
    public List<Merchant> findConnectedBySchool(UUID schoolId) {
        return em.createQuery("""
                        SELECT m FROM Merchant m WHERE m.schoolId = :schoolId AND m.status <> :disconnected
                        ORDER BY m.createdAt, m.id
                        """, Merchant.class)
                .setParameter("schoolId", schoolId)
                .setParameter("disconnected", Merchant.Status.DISCONNECTED)
                .getResultList();
    }

    @Override
    public List<Merchant> findActiveBySchool(UUID schoolId) {
        return em.createQuery("""
                        SELECT m FROM Merchant m WHERE m.schoolId = :schoolId AND m.status = :active
                        ORDER BY m.createdAt, m.id
                        """, Merchant.class)
                .setParameter("schoolId", schoolId)
                .setParameter("active", Merchant.Status.ACTIVE)
                .getResultList();
    }

    @Override
    public long countConnectedBySchool(UUID schoolId) {
        return em.createQuery(
                        "SELECT COUNT(m) FROM Merchant m WHERE m.schoolId = :schoolId AND m.status <> :disconnected",
                        Long.class)
                .setParameter("schoolId", schoolId)
                .setParameter("disconnected", Merchant.Status.DISCONNECTED)
                .getSingleResult();
    }
}
