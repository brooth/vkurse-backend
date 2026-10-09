/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import space.vkurse.core.error.Errors.NotFoundException;
import space.vkurse.core.notification.Content;
import space.vkurse.core.notification.Message;
import space.vkurse.core.notification.NotificationService;
import space.vkurse.core.notification.Target;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.PlatformProperties;
import space.vkurse.platform.school.domain.InvitationTokens;
import space.vkurse.platform.school.domain.Membership;
import space.vkurse.platform.school.domain.School;
import space.vkurse.platform.school.domain.SchoolInvitation;
import space.vkurse.platform.school.domain.SchoolInvitationRepository;
import space.vkurse.platform.school.domain.SchoolMemberRepository;
import space.vkurse.platform.school.domain.SchoolRepository;
import space.vkurse.platform.school.domain.SchoolRole;
import space.vkurse.platform.user.application.UserService;
import space.vkurse.platform.user.domain.Email;
import space.vkurse.platform.user.domain.User;

/**
 * Invitations to the staff of a school. The rules of an invitation are in {@link SchoolInvitation}. The staff manage
 * the invitations of their school; the invited person answers one with the token from the emailed link.
 */
@Service
public class SchoolInvitationService {
    private final SchoolInvitationRepository invitations;
    private final SchoolRepository schools;
    private final SchoolMemberRepository members;
    private final SchoolMemberService staff;
    private final UserService users;
    private final InvitationTokens tokens;
    private final NotificationService notifications;
    private final PlatformProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SchoolInvitationService(
            SchoolInvitationRepository invitations,
            SchoolRepository schools,
            SchoolMemberRepository members,
            SchoolMemberService staff,
            UserService users,
            InvitationTokens tokens,
            NotificationService notifications,
            PlatformProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.invitations = invitations;
        this.schools = schools;
        this.members = members;
        this.staff = staff;
        this.users = users;
        this.tokens = tokens;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Invites an email to the school and sends it the link. An earlier invitation of that email is replaced, so its
     * link stops working. The email goes out after the invitation is committed, so no database connection is held
     * while talking to the mail server; if sending fails, the invitation is removed and the request fails.
     */
    public SchoolInvitation invite(UUID userId, UUID schoolId, String address, SchoolRole role) {
        var email = new Email(address);
        var token = tokens.random();
        var letter = Objects.requireNonNull(transaction.execute(status -> {
            var now = clock.instant();
            var membership = members.findMembership(schoolId, userId)
                    .orElseThrow(() -> new NotFoundException("School not found"));
            var invited = users.findByEmail(email);
            var alreadyMember = invited.flatMap(user -> members.findMembership(schoolId, user.getId()))
                    .isPresent();
            invitations.findBySchoolAndEmail(schoolId, email).ifPresent(invitations::remove);
            var invitation = invitations.save(SchoolInvitation.issue(
                    membership.member(),
                    email,
                    role,
                    alreadyMember,
                    invitations.countLiveBySchool(schoolId, now),
                    token,
                    tokens,
                    now));
            return new Letter(
                    invitation,
                    membership.school().getName(),
                    users.getMe(userId).getName(),
                    invited.map(User::getLocale).orElse(User.DEFAULT_LOCALE));
        }));
        var invitation = letter.invitation();
        try {
            // Sent at once, never submitted: a stored message would keep the token.
            notifications.send(Message.of(
                    Content.template(
                            "school-invitation",
                            letter.locale(),
                            Map.of(
                                    "schoolName", letter.schoolName(),
                                    "inviterName", letter.inviterName(),
                                    "role", role.name(),
                                    "link", link(invitation.getId(), token),
                                    "days", SchoolInvitation.TTL.toDays())),
                    Target.email(email.value())));
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(status -> invitations.removeById(invitation.getId()));
            throw e;
        }
        return invitation;
    }

    /** The invitations of the school that can still be accepted. */
    @Transactional(readOnly = true)
    public List<SchoolInvitation> list(UUID userId, UUID schoolId, Paging paging) {
        staff.require(schoolId, userId).ensureCanManageInvitations();
        return invitations.findLiveBySchool(schoolId, clock.instant(), paging.limit(), paging.offset());
    }

    /** Takes an invitation back: its link stops working. */
    @Transactional
    public void revoke(UUID userId, UUID schoolId, UUID invitationId) {
        staff.require(schoolId, userId).ensureCanManageInvitations();
        var invitation = invitations
                .findById(invitationId)
                .filter(found -> found.belongsTo(schoolId))
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        invitations.remove(invitation);
    }

    /** What the holder of a link is invited to, to show before they answer. */
    @Transactional(readOnly = true)
    public Offer view(UUID invitationId, String token) {
        var invitation = invitations.findById(invitationId).orElseThrow(SchoolInvitationService::notFound);
        invitation.verify(token, tokens, clock.instant());
        var school = schools.findById(invitation.getSchoolId()).orElseThrow(SchoolInvitationService::notFound);
        var inviter =
                users.findById(invitation.getInvitedBy()).map(User::getName).orElse(null);
        return new Offer(school, invitation.getRole(), inviter, invitation.getExpiresAt());
    }

    /** Makes the caller a member of the school they were invited to. The invitation is used up. */
    @Transactional
    public Membership accept(UUID userId, UUID invitationId, String token) {
        var invitation = invitations.findByIdForUpdate(invitationId).orElseThrow(SchoolInvitationService::notFound);
        var school = schools.findById(invitation.getSchoolId()).orElseThrow(SchoolInvitationService::notFound);
        var alreadyMember = members.findMembership(school.getId(), userId).isPresent();
        var member = invitation.accept(userId, alreadyMember, token, tokens, clock.instant());
        members.save(member);
        invitations.remove(invitation);
        return new Membership(school, member);
    }

    /** Turns an invitation down: its link stops working. */
    @Transactional
    public void decline(UUID invitationId, String token) {
        var invitation = invitations.findByIdForUpdate(invitationId).orElseThrow(SchoolInvitationService::notFound);
        invitation.verify(token, tokens, clock.instant());
        invitations.remove(invitation);
    }

    /** The token is in the fragment, which browsers send to no server and put in no referrer. */
    private String link(UUID invitationId, String token) {
        var base = properties.webUrl().toString();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/invitations/" + invitationId + "#"
                + token;
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Invitation not found");
    }

    /** What the accept page shows: the school, the offered role and who invited ({@code null} if they are gone). */
    public record Offer(
            School school, SchoolRole role, @Nullable String inviterName, Instant expiresAt) {}

    /** What the invitation email needs, read in the transaction that issued the invitation. */
    private record Letter(SchoolInvitation invitation, String schoolName, String inviterName, String locale) {}
}
