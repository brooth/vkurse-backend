/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where schools are kept. Deleted schools are never returned. */
public interface SchoolRepository {
    Optional<School> findById(UUID id);

    Optional<School> findBySlug(Slug slug);

    /** The schools the user studies in, the latest joined first. */
    List<School> findByStudent(UUID userId, int limit, int offset);

    School save(School school);
}
