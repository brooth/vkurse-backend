/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.course.application.CourseStructureService;
import vkurse.platform.course.domain.Course;

/** The sections of a course and the order of what is in them, for those who may edit the course. */
@RestController
class SectionController {
    private final CourseStructureService structure;

    SectionController(CourseStructureService structure) {
        this.structure = structure;
    }

    /** Adds a section at the end of the course. */
    @PostMapping("/courses/{courseId}/sections")
    SectionResponse add(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody TitleRequest data) {
        return SectionResponse.of(structure.addSection(session.userId(), courseId, data.title()));
    }

    /** Puts the course's sections in the order of {@code ids}, which must name each of them once. */
    @PostMapping("/courses/{courseId}/sections/order")
    List<SectionResponse> arrange(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody OrderRequest data) {
        return structure.arrangeSections(session.userId(), courseId, data.ids()).stream()
                .map(SectionResponse::of)
                .toList();
    }

    @PatchMapping("/sections/{id}")
    SectionResponse rename(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody TitleRequest data) {
        return SectionResponse.of(structure.renameSection(session.userId(), id, data.title()));
    }

    /** Removes a section that has no lessons. */
    @DeleteMapping("/sections/{id}")
    ResponseEntity<Void> remove(@GetSession UserSession session, @PathVariable UUID id) {
        structure.removeSection(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** Starts a lesson at the end of the section, as a draft. */
    @PostMapping("/sections/{id}/lessons")
    LessonResponse draftLesson(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody TitleRequest data) {
        return LessonResponse.of(structure.draftLesson(session.userId(), id, data.title()));
    }

    /**
     * Puts the section's lessons in the order of {@code ids}. The list must name every lesson the section has; a
     * lesson of another section of the course named in it moves into this one.
     */
    @PostMapping("/sections/{id}/lessons/order")
    List<LessonSummaryResponse> arrangeLessons(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody OrderRequest data) {
        return structure.arrangeLessons(session.userId(), id, data.ids()).stream()
                .map(LessonSummaryResponse::of)
                .toList();
    }

    record TitleRequest(
            @NotBlank @Size(max = Course.MAX_TITLE_LENGTH) String title) {}

    record OrderRequest(
            @NotNull @Size(max = Course.MAX_LESSONS) List<@NotNull UUID> ids) {}
}
