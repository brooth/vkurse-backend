/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import org.springframework.http.HttpStatus;
import space.vkurse.core.error.Errors.ClientException;

/** The school module's client errors. */
public final class SchoolErrors {

    private SchoolErrors() {}

    /** 409 {@code school/slug-taken}: another school has this address. */
    public static class SlugTakenException extends ClientException {
        public SlugTakenException() {
            super(HttpStatus.CONFLICT, "school/slug-taken", "This address is taken by another school");
        }
    }

    /** 409 {@code school/already-member}: the user is on the staff of the school already. */
    public static class AlreadyMemberException extends ClientException {
        public AlreadyMemberException() {
            super(HttpStatus.CONFLICT, "school/already-member", "This user is already a member of the school");
        }
    }

    /** 400 {@code school/invitation-expired}: the invitation is too old. */
    public static class InvitationExpiredException extends ClientException {
        public InvitationExpiredException() {
            super(HttpStatus.BAD_REQUEST, "school/invitation-expired", "The invitation has expired, ask for a new one");
        }
    }

    /** 429 {@code school/too-many-invitations}: the school has too many invitations nobody answered yet. */
    public static class TooManyInvitationsException extends ClientException {
        public TooManyInvitationsException() {
            super(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "school/too-many-invitations",
                    "The school has too many pending invitations, revoke some first");
        }
    }

    /** 409 {@code school/owner-cannot-leave}: an owner can't leave their school, or delete their account. */
    public static class OwnerCannotLeaveException extends ClientException {
        public OwnerCannotLeaveException() {
            super(
                    HttpStatus.CONFLICT,
                    "school/owner-cannot-leave",
                    "Transfer the school to another member or delete it first");
        }
    }
}
