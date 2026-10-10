/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.domain.CourseCard;
import vkurse.platform.school.domain.School;

/** A published course as anyone sees it in the catalog. */
public record CatalogCourseResponse(
        UUID id,
        String title,
        String slug,
        @Nullable String summary,
        @Nullable String cover,
        @Nullable Instant publishedAt,
        long lessons,
        SchoolCard school) {

    public static CatalogCourseResponse of(CourseCard card) {
        var course = card.course();
        return new CatalogCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getSlug().value(),
                course.getSummary(),
                course.getCover(),
                course.getPublishedAt(),
                card.lessons(),
                SchoolCard.of(card.school()));
    }

    /** The school a course belongs to. */
    public record SchoolCard(
            UUID id, String name, String slug, @Nullable String logo) {

        static SchoolCard of(School school) {
            return new SchoolCard(
                    school.getId(), school.getName(), school.getSlug().value(), school.getLogo());
        }
    }
}
