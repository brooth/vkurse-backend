/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.domain;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import vkurse.core.domain.AggregateRoot;
import vkurse.core.error.Errors.NotFoundException;
import vkurse.platform.school.domain.SchoolErrors.AlreadyMemberException;
import vkurse.platform.school.domain.SchoolErrors.InvitationExpiredException;
import vkurse.platform.school.domain.SchoolErrors.TooManyInvitationsException;
import vkurse.platform.user.domain.Email;

/**
 * An invitation to join the staff of a school, sent to an email as a link with a secret token. Whoever is signed in
 * and holds the link may accept it: the email only says where the link went. Only a hash of the token is kept. An
 * invitation lives {@link #TTL}; a school has one per email and at most {@link #MAX_PENDING} at a time. A row exists
 * only while the invitation is pending: an accepted, declined or revoked one is removed.
 */
@Entity
@Table(name = "school_invitations")
public class SchoolInvitation extends AggregateRoot {
    public static final Duration TTL = Duration.ofDays(7);
    public static final int MAX_PENDING = 50;

    private UUID schoolId;

    @Convert(converter = Email.Converter.class)
    private Email email;

    @Enumerated(EnumType.STRING)
    private SchoolRole role;

    /** The user who invited. */
    private UUID invitedBy;

    private String tokenHash;

    private Instant expiresAt;

    protected SchoolInvitation() {}

    private SchoolInvitation(SchoolMember inviter, Email email, SchoolRole role, String tokenHash, Instant now) {
        super(now);
        this.schoolId = inviter.getSchoolId();
        this.email = email;
        this.role = role;
        this.invitedBy = inviter.getUserId();
        this.tokenHash = tokenHash;
        this.expiresAt = now.plus(TTL);
    }

    /**
     * Invites {@code email} to the inviter's school as {@code role}. The caller emails {@code token}; only its hash is
     * kept.
     *
     * @param alreadyMember whether a user with this email is on the staff already
     * @param pending how many live invitations the school has, not counting one to this email that this one replaces
     * @throws AlreadyMemberException {@code alreadyMember}
     * @throws TooManyInvitationsException the school has {@link #MAX_PENDING} live invitations
     */
    public static SchoolInvitation issue(
            SchoolMember inviter,
            Email email,
            SchoolRole role,
            boolean alreadyMember,
            long pending,
            String token,
            InvitationTokens tokens,
            Instant now) {
        inviter.ensureCanInvite(role);
        if (alreadyMember) {
            throw new AlreadyMemberException();
        }
        if (pending >= MAX_PENDING) {
            throw new TooManyInvitationsException();
        }
        return new SchoolInvitation(inviter, email, role, tokens.hash(token), now);
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public Email getEmail() {
        return email;
    }

    public SchoolRole getRole() {
        return role;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean belongsTo(UUID schoolId) {
        return this.schoolId.equals(schoolId);
    }

    /**
     * Checks the token of the link. A wrong token is answered like a missing invitation, so an id alone tells nothing.
     *
     * @throws NotFoundException the token is wrong
     * @throws InvitationExpiredException the invitation is too old
     */
    public void verify(String presentedToken, InvitationTokens tokens, Instant now) {
        var matches = MessageDigest.isEqual(
                tokenHash.getBytes(StandardCharsets.UTF_8),
                tokens.hash(presentedToken.trim()).getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new NotFoundException("Invitation not found");
        }
        if (!now.isBefore(expiresAt)) {
            throw new InvitationExpiredException();
        }
    }

    /**
     * Accepts the invitation for {@code userId} and returns the member they become. Save the member and remove the
     * invitation.
     *
     * @param alreadyMember whether the user is on the staff of the school already
     * @throws NotFoundException the token is wrong
     * @throws InvitationExpiredException the invitation is too old
     * @throws AlreadyMemberException {@code alreadyMember}
     */
    public SchoolMember accept(
            UUID userId, boolean alreadyMember, String presentedToken, InvitationTokens tokens, Instant now) {
        verify(presentedToken, tokens, now);
        if (alreadyMember) {
            throw new AlreadyMemberException();
        }
        return SchoolMember.join(schoolId, userId, role, now);
    }
}
