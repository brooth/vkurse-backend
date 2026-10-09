/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import space.vkurse.core.persistence.JpaAggregateRepository;
import space.vkurse.platform.school.domain.School;
import space.vkurse.platform.school.domain.SchoolRepository;
import space.vkurse.platform.school.domain.Slug;

@Repository
class JpaSchoolRepository extends JpaAggregateRepository<School> implements SchoolRepository {

    JpaSchoolRepository() {
        super(School.class);
    }

    @Override
    public Optional<School> findById(UUID id) {
        return super.findById(id).filter(school -> !school.isDeleted());
    }

    @Override
    public Optional<School> findBySlug(Slug slug) {
        return em.createQuery("SELECT s FROM School s WHERE s.slug = :slug AND s.deletedAt IS NULL", School.class)
                .setParameter("slug", slug)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<School> findByStudent(UUID userId, int limit, int offset) {
        return em.createQuery("""
                        SELECT s FROM SchoolStudent st JOIN School s ON s.id = st.schoolId
                        WHERE st.userId = :userId AND s.deletedAt IS NULL
                        ORDER BY st.createdAt DESC
                        """, School.class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }
}
