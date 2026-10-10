/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Files. A deleted file is found by nothing but {@link #findRemovable}. */
public interface StoredFileRepository {

    Optional<StoredFile> findById(UUID id);

    Optional<StoredFile> findByIdForUpdate(UUID id);

    List<StoredFile> findByIds(Collection<UUID> ids);

    /** How many uploads the user started and has neither confirmed nor let expire. */
    long countPendingByUploader(UUID userId, Instant now);

    /** The confirmed files of a school for those purposes, newest first; {@code search} looks in their names. */
    List<StoredFile> findBySchool(
            UUID schoolId, Set<FilePurpose> purposes, @Nullable String search, int limit, int offset);

    /** Files whose objects and rows may go: the deleted ones, and uploads that expired unconfirmed. Oldest first. */
    /** The bytes of confirmed, not deleted files each school stores, by school id. */
    Map<UUID, Long> sumSizeBySchool();

    /** The bytes of confirmed, not deleted files a school stores. */
    long sumSize(UUID schoolId);

    List<StoredFile> findRemovable(Instant now, int limit);

    StoredFile save(StoredFile file);

    /** Removes the rows, once storage no longer holds their objects. */
    int removeAll(Collection<UUID> ids);
}
