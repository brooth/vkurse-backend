/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.school.application.SchoolInvitationService;
import vkurse.platform.school.application.SchoolInvitationService.Offer;
import vkurse.platform.school.domain.SchoolRole;

/**
 * An invitation as the invited person answers it: with the id and the token from the emailed link. The token travels
 * in the body, never in the URL.
 */
@RestController
@RequestMapping("/invitations/{id}")
class InvitationController {
    private final SchoolInvitationService invitations;

    InvitationController(SchoolInvitationService invitations) {
        this.invitations = invitations;
    }

    /** What the link invites to, to show before answering. */
    @PostMapping("/view")
    OfferResponse view(@PathVariable UUID id, @Valid @RequestBody TokenRequest data) {
        return OfferResponse.of(invitations.view(id, data.token()));
    }

    /** Joins the school. Returns it with the caller's new role. */
    @PostMapping("/accept")
    SchoolResponse accept(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody TokenRequest data) {
        return SchoolResponse.of(invitations.accept(session.userId(), id, data.token()));
    }

    @PostMapping("/decline")
    ResponseEntity<Void> decline(@PathVariable UUID id, @Valid @RequestBody TokenRequest data) {
        invitations.decline(id, data.token());
        return ResponseEntity.noContent().build();
    }

    record TokenRequest(@NotBlank @Size(max = 200) String token) {}

    record OfferResponse(
            SchoolPublicResponse school,
            SchoolRole role,
            @Nullable String inviterName,
            Instant expiresAt) {

        static OfferResponse of(Offer offer) {
            return new OfferResponse(
                    SchoolPublicResponse.of(offer.school()), offer.role(), offer.inviterName(), offer.expiresAt());
        }
    }
}
