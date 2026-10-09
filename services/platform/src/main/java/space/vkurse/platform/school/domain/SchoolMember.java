/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import space.vkurse.core.domain.AggregateRoot;
import space.vkurse.core.error.Errors.BadRequestException;
import space.vkurse.core.error.Errors.ForbiddenException;
import space.vkurse.platform.school.domain.SchoolErrors.OwnerCannotLeaveException;

/**
 * A user on the staff of a school, with a role. A user is a member of a school at most once, and a school has
 * exactly one {@link SchoolRole#OWNER}. What a member may do is asked of the member: the {@code ensure…} methods throw
 * 403 {@code forbidden}.
 */
@Entity
@Table(name = "school_members")
@Getter
public class SchoolMember extends AggregateRoot {
    private UUID schoolId;

    private UUID userId;

    @Enumerated(EnumType.STRING)
    private SchoolRole role;

    protected SchoolMember() {}

    private SchoolMember(UUID schoolId, UUID userId, SchoolRole role, Instant now) {
        super(now);
        this.schoolId = schoolId;
        this.userId = userId;
        this.role = role;
    }

    /** The owner of a school that was just opened. */
    public static SchoolMember owner(School school, UUID userId, Instant now) {
        return new SchoolMember(school.getId(), userId, SchoolRole.OWNER, now);
    }

    /** A member who accepted an invitation; see {@link SchoolInvitation#accept}. */
    static SchoolMember join(UUID schoolId, UUID userId, SchoolRole role, Instant now) {
        return new SchoolMember(schoolId, userId, role, now);
    }

    public boolean belongsTo(UUID schoolId) {
        return this.schoolId.equals(schoolId);
    }

    public void ensureCanEditSchool() {
        ensure(role.canEditSchool());
    }

    public void ensureCanDeleteSchool() {
        ensure(role == SchoolRole.OWNER);
    }

    public void ensureCanManageInvitations() {
        ensure(role.canManageInvitations());
    }

    public void ensureCanManageStudents() {
        ensure(role.canManageStudents());
    }

    /** Checks that this member may invite someone to their school as {@code invited}. */
    public void ensureCanInvite(SchoolRole invited) {
        ensureAssignable(invited);
        ensure(role.canManage(invited));
    }

    /** Gives this member another role, on behalf of {@code actor}, who must manage both the old and the new one. */
    public void changeRole(SchoolRole newRole, SchoolMember actor) {
        ensureAssignable(newRole);
        ensureColleague(actor);
        ensure(actor.role.canManage(role) && actor.role.canManage(newRole));
        role = newRole;
    }

    /**
     * Checks that {@code actor} may remove this member. Anyone may remove themselves (leave), except the owner.
     *
     * @throws OwnerCannotLeaveException this member owns the school
     */
    public void ensureCanBeRemovedBy(SchoolMember actor) {
        ensureColleague(actor);
        if (role == SchoolRole.OWNER) {
            throw new OwnerCannotLeaveException();
        }
        ensure(actor.equals(this) || actor.role.canManage(role));
    }

    /**
     * The first half of a transfer: the owner steps down to admin. Save this member before the successor
     * {@linkplain #takeOverFrom takes over}, because the database allows a school only one owner at any moment.
     */
    public void handOverTo(SchoolMember successor) {
        ensureColleague(successor);
        ensure(role == SchoolRole.OWNER);
        if (successor.equals(this)) {
            throw new BadRequestException("The school is already yours");
        }
        role = SchoolRole.ADMIN;
    }

    /** The second half of a transfer: this member becomes the owner {@code previousOwner} just stopped being. */
    public void takeOverFrom(SchoolMember previousOwner) {
        ensureColleague(previousOwner);
        if (previousOwner.equals(this) || previousOwner.role == SchoolRole.OWNER) {
            throw new IllegalStateException("The owner must hand the school over first");
        }
        role = SchoolRole.OWNER;
    }

    private static void ensureAssignable(SchoolRole role) {
        if (role == SchoolRole.OWNER) {
            throw new BadRequestException("A school gets a new owner only by a transfer");
        }
    }

    private void ensureColleague(SchoolMember other) {
        if (!other.schoolId.equals(schoolId)) {
            throw new IllegalArgumentException("The members are of different schools");
        }
    }

    private static void ensure(boolean allowed) {
        if (!allowed) {
            throw new ForbiddenException("Your role in the school doesn't allow this");
        }
    }
}
