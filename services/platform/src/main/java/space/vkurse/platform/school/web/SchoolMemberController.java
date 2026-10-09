/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.application.SchoolMemberService;
import space.vkurse.platform.school.domain.SchoolRole;

/** The staff of a school. Only its staff may call these; what each role may do is decided by the member. */
@RestController
@RequestMapping("/schools/{schoolId}")
class SchoolMemberController {
    private final SchoolMemberService members;

    SchoolMemberController(SchoolMemberService members) {
        this.members = members;
    }

    @GetMapping("/members")
    List<MemberResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return members.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(MemberResponse::of)
                .toList();
    }

    @PatchMapping("/members/{memberId}")
    MemberResponse changeRole(
            @GetSession UserSession session,
            @PathVariable UUID schoolId,
            @PathVariable UUID memberId,
            @Valid @RequestBody ChangeRoleRequest data) {
        return MemberResponse.of(members.changeRole(session.userId(), schoolId, memberId, data.role()));
    }

    /** Removes a member. With the caller's own member id, the caller leaves the school. */
    @DeleteMapping("/members/{memberId}")
    ResponseEntity<Void> remove(
            @GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID memberId) {
        members.remove(session.userId(), schoolId, memberId);
        return ResponseEntity.noContent().build();
    }

    /** Transfers the school to another member, who is returned as its owner. The caller becomes an admin. */
    @PostMapping("/owner")
    MemberResponse transferOwnership(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody TransferRequest data) {
        return MemberResponse.of(members.transferOwnership(session.userId(), schoolId, data.memberId()));
    }

    record ChangeRoleRequest(@NotNull SchoolRole role) {}

    record TransferRequest(@NotNull UUID memberId) {}
}
