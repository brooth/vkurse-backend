/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.commerce.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.commerce.domain.PromoCode;
import vkurse.platform.commerce.domain.PromoCodeRepository;
import vkurse.platform.commerce.domain.PromoCodeText;

@Repository
class JpaPromoCodeRepository extends JpaAggregateRepository<PromoCode> implements PromoCodeRepository {

    JpaPromoCodeRepository() {
        super(PromoCode.class);
    }

    @Override
    public Optional<PromoCode> findBySchoolAndCode(UUID schoolId, PromoCodeText code) {
        return em
                .createQuery(
                        "SELECT p FROM PromoCode p WHERE p.schoolId = :schoolId AND p.code = :code AND p.archivedAt IS NULL",
                        PromoCode.class)
                .setParameter("schoolId", schoolId)
                .setParameter("code", code)
                .getResultList()
                .stream()
                .findFirst();
    }

    @Override
    public List<PromoCode> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery("""
                        SELECT p FROM PromoCode p WHERE p.schoolId = :schoolId AND p.archivedAt IS NULL
                        ORDER BY p.createdAt DESC, p.id DESC
                        """, PromoCode.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public long countBySchool(UUID schoolId) {
        return em.createQuery(
                        "SELECT COUNT(p) FROM PromoCode p WHERE p.schoolId = :schoolId AND p.archivedAt IS NULL",
                        Long.class)
                .setParameter("schoolId", schoolId)
                .getSingleResult();
    }
}
