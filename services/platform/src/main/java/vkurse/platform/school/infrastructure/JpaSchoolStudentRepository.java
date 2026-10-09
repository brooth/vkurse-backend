/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.school.domain.SchoolStudent;
import vkurse.platform.school.domain.SchoolStudentRepository;
import vkurse.platform.school.domain.StudentProfile;

@Repository
class JpaSchoolStudentRepository extends JpaAggregateRepository<SchoolStudent> implements SchoolStudentRepository {

    JpaSchoolStudentRepository() {
        super(SchoolStudent.class);
    }

    @Override
    public Optional<SchoolStudent> find(UUID schoolId, UUID userId) {
        return em.createQuery(
                        "SELECT s FROM SchoolStudent s WHERE s.schoolId = :schoolId AND s.userId = :userId",
                        SchoolStudent.class)
                .setParameter("schoolId", schoolId)
                .setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    @Override
    public List<StudentProfile> findBySchool(UUID schoolId, @Nullable String search, int limit, int offset) {
        var matching = search == null
                ? ""
                : "AND (LOWER(u.name) LIKE :pattern ESCAPE '\\' OR CAST(u.email AS String) LIKE :pattern ESCAPE '\\')";
        var query =
                em.createQuery("""
                        SELECT new vkurse.platform.school.domain.StudentProfile(s, u)
                        FROM SchoolStudent s JOIN User u ON u.id = s.userId
                        WHERE s.schoolId = :schoolId AND u.deletedAt IS NULL %s
                        ORDER BY s.createdAt DESC
                        """.formatted(matching), StudentProfile.class).setParameter("schoolId", schoolId);
        if (search != null) {
            query.setParameter("pattern", "%" + escapeLike(search) + "%");
        }
        return query.setMaxResults(limit).setFirstResult(offset).getResultList();
    }

    @Override
    public int deleteByUser(UUID userId) {
        return em.createQuery("DELETE FROM SchoolStudent s WHERE s.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    /** Makes the wildcards of LIKE in a search text plain characters. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
