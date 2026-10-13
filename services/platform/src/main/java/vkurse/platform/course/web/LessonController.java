/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.course.application.CourseStructureService;
import vkurse.platform.course.application.LessonService;
import vkurse.platform.course.domain.Course;
import vkurse.platform.course.domain.Drip;
import vkurse.platform.course.domain.LessonBlock;
import vkurse.platform.course.domain.LessonContent;

/** A lesson, for the staff of its course's school. */
@RestController
@RequestMapping("/lessons/{id}")
class LessonController {
    private final LessonService lessons;
    private final CourseStructureService structure;

    LessonController(LessonService lessons, CourseStructureService structure) {
        this.lessons = lessons;
        this.structure = structure;
    }

    @GetMapping
    LessonResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return LessonResponse.of(lessons.get(session.userId(), id));
    }

    /** Changes the title, and who sees the lesson when. */
    @PatchMapping
    LessonResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        var drip = data.drip() == null
                ? null
                : Drip.of(data.drip().kind(), data.drip().days(), data.drip().at());
        return LessonResponse.of(lessons.update(session.userId(), id, data.title(), data.preview(), drip, data.stop()));
    }

    /**
     * Replaces the lesson's content with these blocks. A block is {@code { type: "text", text }} (Markdown) or
     * {@code { type: "video" | "image" | "file", fileId }}, with a file of the school's library, or
     * {@code { type: "embed", url }}, with a link to a video on Rutube, VK Video or YouTube. A block sent with its
     * {@code id} keeps it; one without gets a new id.
     */
    @PostMapping("/content")
    LessonResponse changeContent(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody ContentRequest data) {
        return LessonResponse.of(lessons.changeContent(session.userId(), id, data.blocks()));
    }

    /** Shows the lesson outside the staff. It needs content. */
    @PostMapping("/publish")
    LessonResponse publish(@GetSession UserSession session, @PathVariable UUID id) {
        return LessonResponse.of(lessons.publish(session.userId(), id));
    }

    @PostMapping("/unpublish")
    LessonResponse unpublish(@GetSession UserSession session, @PathVariable UUID id) {
        return LessonResponse.of(lessons.unpublish(session.userId(), id));
    }

    /** Signed links to the files the lesson's blocks show, by block id. They are in the body, never a redirect. */
    @PostMapping("/links")
    Map<UUID, LinkResponse> links(@GetSession UserSession session, @PathVariable UUID id) {
        return LinkResponse.of(lessons.links(session.userId(), id));
    }

    @DeleteMapping
    ResponseEntity<Void> remove(@GetSession UserSession session, @PathVariable UUID id) {
        structure.removeLesson(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    /** {@code null} leaves a field unchanged. */
    record UpdateRequest(
            @Size(min = 1, max = Course.MAX_TITLE_LENGTH) @Nullable
            String title,

            @Nullable Boolean preview,
            @Valid @Nullable DripRequest drip,
            @Nullable Boolean stop) {}

    /** {@code days} goes with {@code AFTER_DAYS}, {@code at} with {@code AT_TIME}. */
    record DripRequest(
            @NotNull Drip.Kind kind,
            @Nullable Integer days,
            @Nullable Instant at) {}

    record ContentRequest(
            @NotNull @Size(max = LessonContent.MAX_BLOCKS) List<LessonBlock> blocks) {}
}
