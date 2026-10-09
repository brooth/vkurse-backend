/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.domain.MemberProfile;
import space.vkurse.platform.school.domain.SchoolMember;
import space.vkurse.platform.school.domain.SchoolMemberRepository;
import space.vkurse.platform.school.domain.SchoolRole;
import space.vkurse.platform.user.application.UserService;

/**
 * The staff of a school. {@link #require} is how any module learns what the caller is in a school, and so what they
 * may do there: ask the returned member ({@code member.ensureCanEditSchool()}).
 */
@Service
@Transactional
public class SchoolMemberService {
    private final SchoolMemberRepository members;
    private final UserService users;

    public SchoolMemberService(SchoolMemberRepository members, UserService users) {
        this.members = members;
        this.users = users;
    }

    /**
     * The caller as a member of the school.
     *
     * @throws NotFoundException there is no such school, or the caller is not on its staff
     */
    @Transactional(readOnly = true)
    public SchoolMember require(UUID schoolId, UUID userId) {
        return members.findMembership(schoolId, userId)
                .orElseThrow(() -> new NotFoundException("School not found"))
                .member();
    }

    @Transactional(readOnly = true)
    public List<MemberProfile> list(UUID userId, UUID schoolId, Paging paging) {
        require(schoolId, userId);
        return members.findBySchool(schoolId, paging.limit(), paging.offset());
    }

    public MemberProfile changeRole(UUID userId, UUID schoolId, UUID memberId, SchoolRole role) {
        var actor = require(schoolId, userId);
        var member = lock(schoolId, memberId);
        member.changeRole(role, actor);
        return profile(members.save(member));
    }

    /** Removes a member from the staff. With the caller's own member id, the caller leaves the school. */
    public void remove(UUID userId, UUID schoolId, UUID memberId) {
        var actor = require(schoolId, userId);
        var member = lock(schoolId, memberId);
        member.ensureCanBeRemovedBy(actor);
        members.remove(member);
    }

    /** Makes another member the owner; the caller, who must be the owner, becomes an admin. */
    public MemberProfile transferOwnership(UUID userId, UUID schoolId, UUID memberId) {
        var owner = lock(schoolId, require(schoolId, userId).getId());
        var successor = lock(schoolId, memberId);
        // Two writes in this order: the unique index allows a school one owner at any moment.
        owner.handOverTo(successor);
        members.save(owner);
        successor.takeOverFrom(owner);
        return profile(members.save(successor));
    }

    /** Loads a member of this school under a row lock, so two changes of the same member wait for each other. */
    private SchoolMember lock(UUID schoolId, UUID memberId) {
        return members.findByIdForUpdate(memberId)
                .filter(member -> member.belongsTo(schoolId))
                .orElseThrow(() -> new NotFoundException("Member not found"));
    }

    private MemberProfile profile(SchoolMember member) {
        var user = users.findById(member.getUserId()).orElseThrow(() -> new NotFoundException("Member not found"));
        return new MemberProfile(member, user);
    }
}
