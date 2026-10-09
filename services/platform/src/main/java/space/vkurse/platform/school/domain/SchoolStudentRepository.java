/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Where the students of schools are kept. */
public interface SchoolStudentRepository {
    Optional<SchoolStudent> findById(UUID id);

    Optional<SchoolStudent> find(UUID schoolId, UUID userId);

    /**
     * The students of a school, the latest first.
     *
     * @param search lowercase text to find in the student's name or email; {@code null} for everyone
     */
    List<StudentProfile> findBySchool(UUID schoolId, @Nullable String search, int limit, int offset);

    /** Removes the user from the students of every school; for an account that is being deleted. */
    int deleteByUser(UUID userId);

    SchoolStudent save(SchoolStudent student);

    void remove(SchoolStudent student);
}
