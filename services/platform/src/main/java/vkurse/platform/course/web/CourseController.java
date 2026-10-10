/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.course.application.CourseService;
import vkurse.platform.course.application.CourseStructureService;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.CourseSlug;

/** The courses of a school, for its staff. */
@RestController
class CourseController {
    private final CourseService courses;
    private final CourseStructureService structure;

    CourseController(CourseService courses, CourseStructureService structure) {
        this.courses = courses;
        this.structure = structure;
    }

    /** Starts a course, as a draft. */
    @PostMapping("/schools/{schoolId}/courses")
    CourseResponse draft(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody DraftRequest data) {
        return CourseResponse.of(courses.draft(
                session.userId(), schoolId, data.title(), data.slug(), data.summary(), data.description()));
    }

    /** All courses of the school, drafts too, newest first. */
    @GetMapping("/schools/{schoolId}/courses")
    List<CourseResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return courses.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(CourseResponse::of)
                .toList();
    }

    @GetMapping("/courses/{id}")
    CourseResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return CourseResponse.of(courses.get(session.userId(), id));
    }

    /** The course with its sections and lessons, drafts too. */
    @GetMapping("/courses/{id}/outline")
    OutlineResponse outline(@GetSession UserSession session, @PathVariable UUID id) {
        return OutlineResponse.of(structure.outline(session.userId(), id));
    }

    @PatchMapping("/courses/{id}")
    CourseResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        return CourseResponse.of(
                courses.update(session.userId(), id, data.title(), data.slug(), data.summary(), data.description()));
    }

    /** Puts the course into the catalog. It needs a published lesson. */
    @PostMapping("/courses/{id}/publish")
    CourseResponse publish(@GetSession UserSession session, @PathVariable UUID id) {
        return CourseResponse.of(courses.publish(session.userId(), id));
    }

    @PostMapping("/courses/{id}/unpublish")
    CourseResponse unpublish(@GetSession UserSession session, @PathVariable UUID id) {
        return CourseResponse.of(courses.unpublish(session.userId(), id));
    }

    /** Deletes the course. Only the school's owner and admins may. */
    @DeleteMapping("/courses/{id}")
    ResponseEntity<Void> delete(@GetSession UserSession session, @PathVariable UUID id) {
        courses.delete(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    record DraftRequest(
            @NotBlank @Size(max = Course.MAX_TITLE_LENGTH) String title,

            @NotBlank
            @Size(min = CourseSlug.MIN_LENGTH, max = CourseSlug.MAX_LENGTH)
            @Pattern(regexp = CourseSlug.FORMAT)
            String slug,

            @Size(max = Course.MAX_SUMMARY_LENGTH) @Nullable String summary,

            @Size(max = Course.MAX_DESCRIPTION_LENGTH) @Nullable
            String description) {}

    /** {@code null} leaves a field unchanged; an empty {@code summary} or {@code description} removes it. */
    record UpdateRequest(
            @Size(min = 1, max = Course.MAX_TITLE_LENGTH) @Nullable
            String title,

            @Size(min = CourseSlug.MIN_LENGTH, max = CourseSlug.MAX_LENGTH)
            @Pattern(regexp = CourseSlug.FORMAT)
            @Nullable
            String slug,

            @Size(max = Course.MAX_SUMMARY_LENGTH) @Nullable String summary,

            @Size(max = Course.MAX_DESCRIPTION_LENGTH) @Nullable
            String description) {}
}
