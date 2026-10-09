/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.school.domain.SchoolInvitation;
import vkurse.platform.school.domain.SchoolInvitationRepository;
import vkurse.platform.user.domain.Email;

@Repository
class JpaSchoolInvitationRepository extends JpaAggregateRepository<SchoolInvitation>
        implements SchoolInvitationRepository {

    JpaSchoolInvitationRepository() {
        super(SchoolInvitation.class);
    }

    @Override
    public Optional<SchoolInvitation> findBySchoolAndEmail(UUID schoolId, Email email) {
        return em.createQuery(
                        "SELECT i FROM SchoolInvitation i WHERE i.schoolId = :schoolId AND i.email = :email",
                        SchoolInvitation.class)
                .setParameter("schoolId", schoolId)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<SchoolInvitation> findLiveBySchool(UUID schoolId, Instant now, int limit, int offset) {
        return em.createQuery("""
                        SELECT i FROM SchoolInvitation i
                        WHERE i.schoolId = :schoolId AND i.expiresAt > :now
                        ORDER BY i.createdAt DESC
                        """, SchoolInvitation.class)
                .setParameter("schoolId", schoolId)
                .setParameter("now", now)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public long countLiveBySchool(UUID schoolId, Instant now) {
        return em.createQuery(
                        "SELECT COUNT(i) FROM SchoolInvitation i WHERE i.schoolId = :schoolId AND i.expiresAt > :now",
                        Long.class)
                .setParameter("schoolId", schoolId)
                .setParameter("now", now)
                .getSingleResult();
    }

    @Override
    public void removeById(UUID id) {
        em.createQuery("DELETE FROM SchoolInvitation i WHERE i.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    @Override
    public int deleteExpired(Instant now) {
        return em.createQuery("DELETE FROM SchoolInvitation i WHERE i.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
