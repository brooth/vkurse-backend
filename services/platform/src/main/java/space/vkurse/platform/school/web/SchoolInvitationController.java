/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.application.SchoolInvitationService;
import space.vkurse.platform.school.domain.SchoolRole;

/** The invitations a school has sent, for its owner and admins. */
@RestController
@RequestMapping("/schools/{schoolId}/invitations")
class SchoolInvitationController {
    private final SchoolInvitationService invitations;

    SchoolInvitationController(SchoolInvitationService invitations) {
        this.invitations = invitations;
    }

    @GetMapping
    List<InvitationResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return invitations.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(InvitationResponse::of)
                .toList();
    }

    /** Emails a link to join the school. Inviting the same email again replaces its earlier invitation. */
    @PostMapping
    InvitationResponse invite(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody InviteRequest data) {
        return InvitationResponse.of(invitations.invite(session.userId(), schoolId, data.email(), data.role()));
    }

    @DeleteMapping("/{invitationId}")
    ResponseEntity<Void> revoke(
            @GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID invitationId) {
        invitations.revoke(session.userId(), schoolId, invitationId);
        return ResponseEntity.noContent().build();
    }

    record InviteRequest(
            @NotBlank @Email @Size(max = space.vkurse.platform.user.domain.Email.MAX_LENGTH)
            String email,

            @NotNull SchoolRole role) {}
}
