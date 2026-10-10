/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.commerce.domain.Order;
import vkurse.platform.commerce.domain.OrderRepository;

@Repository
class JpaOrderRepository extends JpaAggregateRepository<Order> implements OrderRepository {

    JpaOrderRepository() {
        super(Order.class);
    }

    @Override
    public List<Order> findByBuyer(UUID buyerId, int limit, int offset) {
        return em.createQuery(
                        "SELECT o FROM Order o WHERE o.buyerId = :buyerId ORDER BY o.createdAt DESC, o.id DESC",
                        Order.class)
                .setParameter("buyerId", buyerId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<Order> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery(
                        "SELECT o FROM Order o WHERE o.schoolId = :schoolId ORDER BY o.createdAt DESC, o.id DESC",
                        Order.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public boolean hasPaid(UUID buyerId, UUID courseId, UUID exceptOrderId) {
        return em.createQuery("""
                        SELECT COUNT(o) FROM Order o
                        WHERE o.buyerId = :buyerId AND o.courseId = :courseId AND o.status = :paid
                          AND o.id <> :except
                        """, Long.class)
                        .setParameter("buyerId", buyerId)
                        .setParameter("courseId", courseId)
                        .setParameter("paid", Order.Status.PAID)
                        .setParameter("except", exceptOrderId)
                        .getSingleResult()
                > 0;
    }

    @Override
    public boolean hasPaid(UUID buyerId, UUID courseId) {
        return em.createQuery(
                                "SELECT COUNT(o) FROM Order o WHERE o.buyerId = :buyerId AND o.courseId = :courseId AND o.status = :paid",
                                Long.class)
                        .setParameter("buyerId", buyerId)
                        .setParameter("courseId", courseId)
                        .setParameter("paid", Order.Status.PAID)
                        .getSingleResult()
                > 0;
    }

    @Override
    public List<Order> findPending(UUID buyerId, UUID courseId) {
        return em.createQuery("""
                        SELECT o FROM Order o
                        WHERE o.buyerId = :buyerId AND o.courseId = :courseId AND o.status = :pending
                        """, Order.class)
                .setParameter("buyerId", buyerId)
                .setParameter("courseId", courseId)
                .setParameter("pending", Order.Status.PENDING)
                .getResultList();
    }

    @Override
    public List<UUID> findOverdue(Instant now, int limit) {
        return em.createQuery(
                        "SELECT o.id FROM Order o WHERE o.status = :pending AND o.expiresAt <= :now ORDER BY o.expiresAt",
                        UUID.class)
                .setParameter("pending", Order.Status.PENDING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }
}
