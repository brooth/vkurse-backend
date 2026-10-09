/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where the staff of schools are kept. A member of a deleted school is found only by id. */
public interface SchoolMemberRepository {
    Optional<SchoolMember> findById(UUID id);

    Optional<SchoolMember> findByIdForUpdate(UUID id);

    /** What the user is in the school, if the school is live and they are on its staff. */
    Optional<Membership> findMembership(UUID schoolId, UUID userId);

    /** The live schools the user works in, the latest joined first. */
    List<Membership> findMemberships(UUID userId, int limit, int offset);

    /** The staff of a school, in the order they joined. */
    List<MemberProfile> findBySchool(UUID schoolId, int limit, int offset);

    /** Whether the user owns a school that is not deleted. */
    boolean ownsLiveSchool(UUID userId);

    /** Removes the user from every school; for an account that is being deleted. */
    int deleteByUser(UUID userId);

    SchoolMember save(SchoolMember member);

    void remove(SchoolMember member);
}
