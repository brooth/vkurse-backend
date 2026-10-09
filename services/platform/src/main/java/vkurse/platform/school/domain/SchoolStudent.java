/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;

/**
 * A user who studies in a school. A user is a student of a school at most once; being on its staff is separate (see
 * {@link SchoolMember}), and a user may be both.
 */
@Entity
@Table(name = "school_students")
@Getter
public class SchoolStudent extends AggregateRoot {
    private UUID schoolId;

    private UUID userId;

    /** The user from the staff who added the student by hand; {@code null} when the student came on their own. */
    private @Nullable UUID addedBy;

    protected SchoolStudent() {}

    private SchoolStudent(UUID schoolId, UUID userId, @Nullable UUID addedBy, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.userId = userId;
        this.addedBy = addedBy;
    }

    public static SchoolStudent enroll(UUID schoolId, UUID userId, @Nullable UUID addedBy, Instant now) {
        return new SchoolStudent(schoolId, userId, addedBy, now);
    }

    public boolean belongsTo(UUID schoolId) {
        return this.schoolId.equals(schoolId);
    }
}
