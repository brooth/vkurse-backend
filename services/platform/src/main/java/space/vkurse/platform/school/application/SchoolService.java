/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.domain.Membership;
import space.vkurse.platform.school.domain.School;
import space.vkurse.platform.school.domain.SchoolErrors.SlugTakenException;
import space.vkurse.platform.school.domain.SchoolMember;
import space.vkurse.platform.school.domain.SchoolMemberRepository;
import space.vkurse.platform.school.domain.SchoolRepository;
import space.vkurse.platform.school.domain.Slug;

/**
 * Use cases around a school itself. A call for a user reaches only the schools they work in: any other school is
 * "not found", like one that doesn't exist.
 */
@Service
@Transactional
public class SchoolService {
    private final SchoolRepository schools;
    private final SchoolMemberRepository members;
    private final Clock clock;

    public SchoolService(SchoolRepository schools, SchoolMemberRepository members, Clock clock) {
        this.schools = schools;
        this.members = members;
        this.clock = clock;
    }

    /** Opens a school; the caller becomes its owner. */
    public Membership open(UUID userId, String name, String slug, @Nullable String description) {
        var now = clock.instant();
        var school = School.open(name, free(new Slug(slug)), description, now);
        schools.save(school);
        return new Membership(school, members.save(SchoolMember.owner(school, userId, now)));
    }

    @Transactional(readOnly = true)
    public Membership get(UUID userId, UUID schoolId) {
        return members.findMembership(schoolId, userId).orElseThrow(() -> new NotFoundException("School not found"));
    }

    /** A school as anyone may see it. */
    @Transactional(readOnly = true)
    public School getBySlug(String slug) {
        return schools.findBySlug(new Slug(slug)).orElseThrow(() -> new NotFoundException("School not found"));
    }

    /** The schools the caller works in. */
    @Transactional(readOnly = true)
    public List<Membership> findMine(UUID userId, Paging paging) {
        return members.findMemberships(userId, paging.limit(), paging.offset());
    }

    /** The schools the caller studies in. */
    @Transactional(readOnly = true)
    public List<School> findStudying(UUID userId, Paging paging) {
        return schools.findByStudent(userId, paging.limit(), paging.offset());
    }

    public Membership update(
            UUID userId, UUID schoolId, @Nullable String name, @Nullable String slug, @Nullable String description) {
        var membership = get(userId, schoolId);
        membership.member().ensureCanEditSchool();
        var school = membership.school();
        var newSlug = slug == null ? null : new Slug(slug);
        school.changeDetails(
                name, newSlug == null || newSlug.equals(school.getSlug()) ? null : free(newSlug), description);
        schools.save(school);
        return membership;
    }

    public void delete(UUID userId, UUID schoolId) {
        var membership = get(userId, schoolId);
        membership.member().ensureCanDeleteSchool();
        membership.school().delete(clock.instant());
        schools.save(membership.school());
    }

    /**
     * Returns the slug if no school has it. Two requests can still take the same free slug at once: the unique index
     * then rejects the second with a plain 409 {@code conflict}.
     */
    private Slug free(Slug slug) {
        if (schools.findBySlug(slug).isPresent()) {
            throw new SlugTakenException();
        }
        return slug;
    }
}
