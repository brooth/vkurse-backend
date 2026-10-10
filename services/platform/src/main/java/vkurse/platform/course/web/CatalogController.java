/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.web.Paging;
import vkurse.platform.course.application.CatalogService;

/** Public: the catalog of published courses. No token is needed for any of these. */
@RestController
@RequestMapping("/catalog")
class CatalogController {
    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Published courses. With {@code $search}, those that match it, best first; without, the latest first.
     * {@code school} (a school's slug) keeps only that school's courses.
     */
    @GetMapping("/courses")
    List<CatalogCourseResponse> search(@RequestParam Map<String, String> params) {
        return catalog.search(params.get("school"), Paging.parse(params)).stream()
                .map(CatalogCourseResponse::of)
                .toList();
    }

    /** A published course's page: the course and the outline of its published lessons. */
    @GetMapping("/schools/{schoolSlug}/courses/{courseSlug}")
    CatalogPageResponse course(@PathVariable String schoolSlug, @PathVariable String courseSlug) {
        return CatalogPageResponse.of(catalog.course(schoolSlug, courseSlug));
    }

    /** A free preview lesson of a published course. */
    @GetMapping("/lessons/{id}")
    PreviewLessonResponse previewLesson(@PathVariable UUID id) {
        return PreviewLessonResponse.of(catalog.previewLesson(id));
    }

    /** Signed links to the files a free preview lesson shows, by block id. */
    @PostMapping("/lessons/{id}/links")
    Map<UUID, LinkResponse> previewLinks(@PathVariable UUID id) {
        return LinkResponse.of(catalog.previewLinks(id));
    }
}
