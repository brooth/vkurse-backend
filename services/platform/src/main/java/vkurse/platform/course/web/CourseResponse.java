/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.Course;

/** A course as the staff of its school see it. */
public record CourseResponse(
        UUID id,
        Instant createdAt,
        @Nullable Instant updatedAt,
        UUID schoolId,
        String title,
        String slug,
        @Nullable String summary,
        @Nullable String description,
        @Nullable String cover,
        Course.Status status,
        @Nullable Instant publishedAt) {

    public static CourseResponse of(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCreatedAt(),
                course.getUpdatedAt(),
                course.getSchoolId(),
                course.getTitle(),
                course.getSlug().value(),
                course.getSummary(),
                course.getDescription(),
                course.getCover(),
                course.getStatus(),
                course.getPublishedAt());
    }
}
