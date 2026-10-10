/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import vkurse.core.domain.AggregateRoot;

/**
 * A student has a course. There is one row per student and course: taking the access away and giving it again keep
 * the row, and with it the student's progress.
 */
@Entity
@Table(name = "enrollments")
@Getter
public class Enrollment extends AggregateRoot {
    private UUID schoolId;

    private UUID courseId;

    private UUID studentId;

    /** How the access was last given. */
    @Enumerated(EnumType.STRING)
    private Source source;

    /** The paid order that last gave the access. */
    private @Nullable UUID orderId;

    /** The user from the staff who last gave the access by hand. */
    private @Nullable UUID grantedBy;

    /** When the student first got the course: "after N days" lessons count from here. */
    private Instant startedAt;

    /** When the access ends by itself; {@code null} for no end. */
    private @Nullable Instant expiresAt;

    /** When the access was taken away. */
    private @Nullable Instant revokedAt;

    protected Enrollment() {}

    private Enrollment(UUID schoolId, UUID courseId, UUID studentId, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.courseId = courseId;
        this.studentId = studentId;
        this.startedAt = now;
    }

    /** Gives a student a course they never had. */
    public static Enrollment open(UUID schoolId, UUID courseId, UUID studentId, AccessGrant grant, Instant now) {
        var enrollment = new Enrollment(schoolId, courseId, studentId, now);
        enrollment.apply(grant, now);
        return enrollment;
    }

    /**
     * Gives the course again to a student who had it. Access that had ended starts anew; access that still lasts gets
     * longer by the granted days, and has no end if either has none. The same order gives access once.
     *
     * @return whether anything changed
     */
    public boolean renew(AccessGrant grant, Instant now) {
        if (grant.orderId() != null && grant.orderId().equals(orderId)) {
            return false;
        }
        apply(grant, now);
        return true;
    }

    private void apply(AccessGrant grant, Instant now) {
        var from = isActive(now) && !isNew() ? expiresAt : now;
        var days = grant.accessDays();
        // `from` is null when the access that still lasts has no end: it keeps none.
        expiresAt = from == null || days == null ? null : from.plus(Duration.ofDays(days));
        revokedAt = null;
        source = grant.source();
        orderId = grant.orderId();
        grantedBy = grant.grantedBy();
    }

    /** Takes the access away. The progress stays. */
    public boolean revoke(Instant now) {
        if (revokedAt != null) {
            return false;
        }
        revokedAt = now;
        return true;
    }

    /** Whether the student has the course now. */
    public boolean isActive(Instant now) {
        return revokedAt == null && (expiresAt == null || now.isBefore(expiresAt));
    }

    public boolean isOf(UUID studentId) {
        return this.studentId.equals(studentId);
    }

    public boolean isGivenBy(UUID orderId) {
        return Objects.equals(this.orderId, orderId);
    }

    public enum Source {
        /** A paid order. */
        ORDER,
        /** Given by the staff. */
        GRANT
    }
}
