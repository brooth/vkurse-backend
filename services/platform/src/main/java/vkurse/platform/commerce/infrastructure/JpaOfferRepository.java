/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.commerce.domain.Offer;
import vkurse.platform.commerce.domain.OfferRepository;

@Repository
class JpaOfferRepository extends JpaAggregateRepository<Offer> implements OfferRepository {

    JpaOfferRepository() {
        super(Offer.class);
    }

    @Override
    public List<Offer> findByCourse(UUID courseId) {
        return em.createQuery("""
                        SELECT o FROM Offer o WHERE o.courseId = :courseId AND o.archivedAt IS NULL
                        ORDER BY o.position, o.id
                        """, Offer.class)
                .setParameter("courseId", courseId)
                .getResultList();
    }

    @Override
    public List<Offer> findActiveByCourse(UUID courseId) {
        return em.createQuery("""
                        SELECT o FROM Offer o WHERE o.courseId = :courseId AND o.active = TRUE AND o.archivedAt IS NULL
                        ORDER BY o.position, o.id
                        """, Offer.class)
                .setParameter("courseId", courseId)
                .getResultList();
    }

    @Override
    public long countByCourse(UUID courseId) {
        return em.createQuery(
                        "SELECT COUNT(o) FROM Offer o WHERE o.courseId = :courseId AND o.archivedAt IS NULL",
                        Long.class)
                .setParameter("courseId", courseId)
                .getSingleResult();
    }

    @Override
    public int nextPosition(UUID courseId) {
        return em.createQuery(
                        "SELECT COALESCE(MAX(o.position), -1) + 1 FROM Offer o WHERE o.courseId = :courseId",
                        Integer.class)
                .setParameter("courseId", courseId)
                .getSingleResult();
    }
}
