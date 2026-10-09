/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

/** What a member of the staff is in a school, and so what they may do there. Every role may see the school. */
public enum SchoolRole {
    /** The one member the school belongs to. May do everything; the role moves only by a transfer. */
    OWNER,
    /** Runs the school with the owner, but can't delete it, and manages only authors and curators. */
    ADMIN,
    /** Builds courses. */
    AUTHOR,
    /** Reviews students' work. */
    CURATOR;

    /** Whether this role may change the school's name, address and description. */
    public boolean canEditSchool() {
        return this == OWNER || this == ADMIN;
    }

    /** Whether this role may see and revoke the school's invitations. */
    public boolean canManageInvitations() {
        return this == OWNER || this == ADMIN;
    }

    /** Whether this role may add and remove students. */
    public boolean canManageStudents() {
        return this == OWNER || this == ADMIN;
    }

    /** Whether this role may upload files to the school's library and delete them. Every role may read them. */
    public boolean canManageFiles() {
        return this == OWNER || this == ADMIN || this == AUTHOR;
    }

    /**
     * Whether this role may invite someone as {@code target}, give a member that role, take it away, or remove a
     * member who has it. Nobody manages an owner, and an admin doesn't manage admins.
     */
    public boolean canManage(SchoolRole target) {
        return switch (this) {
            case OWNER -> target != OWNER;
            case ADMIN -> target == AUTHOR || target == CURATOR;
            case AUTHOR, CURATOR -> false;
        };
    }
}
