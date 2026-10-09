/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import space.vkurse.platform.user.domain.Email;

/** Where pending invitations are kept. */
public interface SchoolInvitationRepository {
    Optional<SchoolInvitation> findById(UUID id);

    Optional<SchoolInvitation> findByIdForUpdate(UUID id);

    /** The invitation a school sent to this email, expired or not: there is at most one. */
    Optional<SchoolInvitation> findBySchoolAndEmail(UUID schoolId, Email email);

    /** The invitations of a school that can still be accepted, the latest first. */
    List<SchoolInvitation> findLiveBySchool(UUID schoolId, Instant now, int limit, int offset);

    long countLiveBySchool(UUID schoolId, Instant now);

    SchoolInvitation save(SchoolInvitation invitation);

    void remove(SchoolInvitation invitation);

    /** Removes an invitation that may not be loaded, e.g. one whose email could not be sent. */
    void removeById(UUID id);

    /** Removes the invitations nobody answered in time; returns how many. */
    int deleteExpired(Instant now);
}
