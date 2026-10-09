/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.time.Instant;
import java.util.UUID;
import vkurse.platform.school.domain.MemberProfile;
import vkurse.platform.school.domain.SchoolRole;

/** A member of a school's staff. {@code createdAt} is when they joined. */
public record MemberResponse(UUID id, Instant createdAt, SchoolRole role, PersonResponse user) {

    public static MemberResponse of(MemberProfile profile) {
        var member = profile.member();
        return new MemberResponse(
                member.getId(), member.getCreatedAt(), member.getRole(), PersonResponse.of(profile.user()));
    }
}
