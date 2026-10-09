/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.infrastructure;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import vkurse.core.persistence.JpaAggregateRepository;
import vkurse.platform.storage.domain.FilePurpose;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.StoredFileRepository;

@Repository
class JpaStoredFileRepository extends JpaAggregateRepository<StoredFile> implements StoredFileRepository {

    JpaStoredFileRepository() {
        super(StoredFile.class);
    }

    @Override
    public Optional<StoredFile> findById(UUID id) {
        return super.findById(id).filter(file -> !file.isDeleted());
    }

    @Override
    public Optional<StoredFile> findByIdForUpdate(UUID id) {
        return super.findByIdForUpdate(id).filter(file -> !file.isDeleted());
    }

    @Override
    public long countPendingByUploader(UUID userId, Instant now) {
        return em.createQuery("""
                        SELECT COUNT(f) FROM StoredFile f
                        WHERE f.uploadedBy = :userId AND f.status = :pending
                          AND f.uploadExpiresAt > :now AND f.deletedAt IS NULL
                        """, Long.class)
                .setParameter("userId", userId)
                .setParameter("pending", StoredFile.Status.PENDING)
                .setParameter("now", now)
                .getSingleResult();
    }

    @Override
    public List<StoredFile> findBySchool(
            UUID schoolId, FilePurpose purpose, @Nullable String search, int limit, int offset) {
        var matching = search == null ? "" : "AND LOWER(f.name) LIKE :pattern ESCAPE '\\'";
        var query = em.createQuery("""
                        SELECT f FROM StoredFile f
                        WHERE f.schoolId = :schoolId AND f.purpose = :purpose AND f.status = :ready
                          AND f.deletedAt IS NULL %s
                        ORDER BY f.createdAt DESC, f.id DESC
                        """.formatted(matching), StoredFile.class)
                .setParameter("schoolId", schoolId)
                .setParameter("purpose", purpose)
                .setParameter("ready", StoredFile.Status.READY);
        if (search != null) {
            query.setParameter("pattern", "%" + escapeLike(search) + "%");
        }
        return query.setMaxResults(limit).setFirstResult(offset).getResultList();
    }

    @Override
    public List<StoredFile> findRemovable(Instant now, int limit) {
        return em.createQuery("""
                        SELECT f FROM StoredFile f
                        WHERE f.deletedAt IS NOT NULL OR (f.status = :pending AND f.uploadExpiresAt < :now)
                        ORDER BY f.createdAt
                        """, StoredFile.class)
                .setParameter("pending", StoredFile.Status.PENDING)
                .setParameter("now", now)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int removeAll(Collection<UUID> ids) {
        return em.createQuery("DELETE FROM StoredFile f WHERE f.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }

    /** Makes the wildcards of LIKE in a search text plain characters. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
