/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.learning.application.EnrollmentService;
import vkurse.platform.learning.domain.AccessGrant;
import vkurse.platform.learning.domain.Enrollment;
import vkurse.platform.school.web.PersonResponse;
import vkurse.platform.user.domain.Email;

/** The staff's view of who has a course. */
@RestController
class EnrollmentController {
    private final EnrollmentService enrollments;

    EnrollmentController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    /** Everyone who has or had the course, the latest first, with how far each is. */
    @GetMapping("/courses/{courseId}/enrollments")
    List<EnrollmentResponse> list(
            @GetSession UserSession session, @PathVariable UUID courseId, @RequestParam Map<String, String> params) {
        return enrollments.list(session.userId(), courseId, Paging.parse(params)).stream()
                .map(EnrollmentResponse::of)
                .toList();
    }

    /**
     * Gives the course to someone by email, for {@code accessDays} or with no end. They become a student of the
     * school, get an account if they have none, and are told by email.
     */
    @PostMapping("/courses/{courseId}/enrollments")
    EnrollmentResponse grant(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody GrantRequest data) {
        return EnrollmentResponse.of(enrollments.grant(session.userId(), courseId, data.email(), data.accessDays()));
    }

    /** Takes the course away. The student's progress stays. */
    @DeleteMapping("/enrollments/{id}")
    ResponseEntity<Void> revoke(@GetSession UserSession session, @PathVariable UUID id) {
        enrollments.revoke(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    record GrantRequest(
            @NotBlank @Size(max = Email.MAX_LENGTH) String email,
            @Min(1) @Max(AccessGrant.MAX_DAYS) @Nullable Integer accessDays) {}

    /** {@code active} says whether the student has the course now. */
    record EnrollmentResponse(
            UUID id,
            Instant createdAt,
            UUID courseId,
            @Nullable PersonResponse student,
            Enrollment.Source source,
            @Nullable UUID orderId,
            @Nullable UUID grantedBy,
            Instant startedAt,
            @Nullable Instant expiresAt,
            @Nullable Instant revokedAt,
            boolean active,
            long lessons,
            long completed) {
        static EnrollmentResponse of(EnrollmentService.StudentProgress progress) {
            var enrollment = progress.enrollment();
            return new EnrollmentResponse(
                    enrollment.getId(),
                    enrollment.getCreatedAt(),
                    enrollment.getCourseId(),
                    progress.student() == null ? null : PersonResponse.of(progress.student()),
                    enrollment.getSource(),
                    enrollment.getOrderId(),
                    enrollment.getGrantedBy(),
                    enrollment.getStartedAt(),
                    enrollment.getExpiresAt(),
                    enrollment.getRevokedAt(),
                    progress.active(),
                    progress.lessons(),
                    progress.completed());
        }
    }
}
