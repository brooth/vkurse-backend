/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import vkurse.platform.course.application.CatalogService;
import vkurse.platform.course.domain.Lesson;
import vkurse.platform.course.domain.Section;

/**
 * A published course's page as anyone sees it: the course, its description, and its sections with the titles of
 * their published lessons. A lesson marked {@code preview} can be read at {@code GET /catalog/lessons/{id}}.
 */
public record CatalogPageResponse(
        CatalogCourseResponse course, @Nullable String description, List<SectionOutline> sections) {

    public static CatalogPageResponse of(CatalogService.Page page) {
        return new CatalogPageResponse(
                CatalogCourseResponse.of(page.card()),
                page.card().course().getDescription(),
                page.sections().stream()
                        .map(section -> SectionOutline.of(section, page.lessons()))
                        .filter(section -> !section.lessons().isEmpty())
                        .toList());
    }

    public record SectionOutline(UUID id, String title, List<LessonTitle> lessons) {

        static SectionOutline of(Section section, List<Lesson> lessons) {
            return new SectionOutline(
                    section.getId(),
                    section.getTitle(),
                    lessons.stream()
                            .filter(lesson -> lesson.isIn(section.getId()))
                            .map(lesson -> new LessonTitle(lesson.getId(), lesson.getTitle(), lesson.isPreview()))
                            .toList());
        }
    }

    public record LessonTitle(UUID id, String title, boolean preview) {}
}
