/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.time.Instant;
import java.util.UUID;
import vkurse.platform.school.domain.SchoolInvitation;
import vkurse.platform.school.domain.SchoolRole;

/** A pending invitation as the staff see it. The token of its link is never shown: it is only in the email. */
public record InvitationResponse(
        UUID id, Instant createdAt, String email, SchoolRole role, UUID invitedBy, Instant expiresAt) {

    public static InvitationResponse of(SchoolInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getCreatedAt(),
                invitation.getEmail().value(),
                invitation.getRole(),
                invitation.getInvitedBy(),
                invitation.getExpiresAt());
    }
}
