/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.school.domain.MemberProfile;
import vkurse.platform.school.domain.Membership;
import vkurse.platform.school.domain.SchoolMember;
import vkurse.platform.school.domain.SchoolMemberRepository;
import vkurse.platform.school.domain.SchoolRole;

@Repository
class JpaSchoolMemberRepository extends JpaAggregateRepository<SchoolMember> implements SchoolMemberRepository {

    JpaSchoolMemberRepository() {
        super(SchoolMember.class);
    }

    @Override
    public Optional<Membership> findMembership(UUID schoolId, UUID userId) {
        return em.createQuery("""
                        SELECT new vkurse.platform.school.domain.Membership(s, m)
                        FROM SchoolMember m JOIN School s ON s.id = m.schoolId
                        WHERE m.schoolId = :schoolId AND m.userId = :userId AND s.deletedAt IS NULL
                        """, Membership.class)
                .setParameter("schoolId", schoolId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<Membership> findMemberships(UUID userId, int limit, int offset) {
        return em.createQuery("""
                        SELECT new vkurse.platform.school.domain.Membership(s, m)
                        FROM SchoolMember m JOIN School s ON s.id = m.schoolId
                        WHERE m.userId = :userId AND s.deletedAt IS NULL
                        ORDER BY m.createdAt DESC
                        """, Membership.class)
                .setParameter("userId", userId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public List<MemberProfile> findBySchool(UUID schoolId, int limit, int offset) {
        return em.createQuery("""
                        SELECT new vkurse.platform.school.domain.MemberProfile(m, u)
                        FROM SchoolMember m JOIN User u ON u.id = m.userId
                        WHERE m.schoolId = :schoolId AND u.deletedAt IS NULL
                        ORDER BY m.createdAt
                        """, MemberProfile.class)
                .setParameter("schoolId", schoolId)
                .setMaxResults(limit)
                .setFirstResult(offset)
                .getResultList();
    }

    @Override
    public boolean ownsLiveSchool(UUID userId) {
        return !em.createQuery("""
                        SELECT m.id FROM SchoolMember m JOIN School s ON s.id = m.schoolId
                        WHERE m.userId = :userId AND m.role = :owner AND s.deletedAt IS NULL
                        """, UUID.class)
                .setParameter("userId", userId)
                .setParameter("owner", SchoolRole.OWNER)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public int deleteByUser(UUID userId) {
        return em.createQuery("DELETE FROM SchoolMember m WHERE m.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }
}
