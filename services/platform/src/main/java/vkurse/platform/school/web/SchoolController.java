/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.core.web.Paging;
import vkurse.platform.school.application.SchoolService;
import vkurse.platform.school.domain.School;
import vkurse.platform.school.domain.Slug;

/** Schools: the ones the caller works or studies in, and the public page of any school. */
@RestController
@RequestMapping("/schools")
class SchoolController {
    private final SchoolService schools;

    SchoolController(SchoolService schools) {
        this.schools = schools;
    }

    /** Opens a school. The caller becomes its owner. */
    @PostMapping
    SchoolResponse open(@GetSession UserSession session, @Valid @RequestBody OpenRequest data) {
        return SchoolResponse.of(schools.open(session.userId(), data.name(), data.slug(), data.description()));
    }

    /** The schools the caller works in, each with their role. */
    @GetMapping
    List<SchoolResponse> findMine(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return schools.findMine(session.userId(), Paging.parse(params)).stream()
                .map(SchoolResponse::of)
                .toList();
    }

    /** The schools the caller studies in. */
    @GetMapping("/studying")
    List<SchoolPublicResponse> findStudying(@GetSession UserSession session, @RequestParam Map<String, String> params) {
        return schools.findStudying(session.userId(), Paging.parse(params)).stream()
                .map(SchoolPublicResponse::of)
                .toList();
    }

    /** Public: a school by its address. */
    @GetMapping("/by-slug/{slug}")
    SchoolPublicResponse getBySlug(@PathVariable String slug) {
        return SchoolPublicResponse.of(schools.getBySlug(slug));
    }

    @GetMapping("/{id}")
    SchoolResponse get(@GetSession UserSession session, @PathVariable UUID id) {
        return SchoolResponse.of(schools.get(session.userId(), id));
    }

    @PatchMapping("/{id}")
    SchoolResponse update(
            @GetSession UserSession session, @PathVariable UUID id, @Valid @RequestBody UpdateRequest data) {
        return SchoolResponse.of(schools.update(session.userId(), id, data.name(), data.slug(), data.description()));
    }

    /** Deletes the school. Only its owner may. */
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@GetSession UserSession session, @PathVariable UUID id) {
        schools.delete(session.userId(), id);
        return ResponseEntity.noContent().build();
    }

    record OpenRequest(
            @NotBlank @Size(max = School.MAX_NAME_LENGTH) String name,

            @NotBlank @Size(min = Slug.MIN_LENGTH, max = Slug.MAX_LENGTH) @Pattern(regexp = Slug.FORMAT)
            String slug,

            @Size(max = School.MAX_DESCRIPTION_LENGTH) @Nullable
            String description) {}

    /** {@code null} leaves a field unchanged; an empty {@code description} removes it. */
    record UpdateRequest(
            @Size(min = 1, max = School.MAX_NAME_LENGTH) @Nullable
            String name,

            @Size(min = Slug.MIN_LENGTH, max = Slug.MAX_LENGTH) @Pattern(regexp = Slug.FORMAT) @Nullable
            String slug,

            @Size(max = School.MAX_DESCRIPTION_LENGTH) @Nullable
            String description) {}
}
