/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.course.application.CourseCoverService;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.Upload;

/** A course's cover, for those who may edit the course. */
@RestController
@RequestMapping("/courses/{courseId}/cover")
class CourseCoverController {
    private final CourseCoverService covers;

    CourseCoverController(CourseCoverService covers) {
        this.covers = covers;
    }

    /** Step 1: asks for a link to upload an image with. */
    @PostMapping("/upload")
    UploadResponse requestUpload(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                covers.requestUpload(session.userId(), courseId, data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the image was sent to storage: makes it the cover. */
    @PostMapping
    CourseResponse change(
            @GetSession UserSession session, @PathVariable UUID courseId, @Valid @RequestBody ChangeRequest data) {
        return CourseResponse.of(covers.change(session.userId(), courseId, data.fileId()));
    }

    @DeleteMapping
    ResponseEntity<Void> remove(@GetSession UserSession session, @PathVariable UUID courseId) {
        covers.remove(session.userId(), courseId);
        return ResponseEntity.noContent().build();
    }

    /** The image a client is about to upload: its name, content type and size in bytes. */
    record UploadRequest(
            @NotBlank @Size(max = StoredFile.MAX_NAME_LENGTH)
            String name,

            @NotBlank @Size(max = 100) String contentType,
            @NotNull @Positive Long size) {}

    /** {@code PUT} the raw image to {@code uploadUrl}, with the declared content type, before {@code expiresAt}. */
    record UploadResponse(UUID fileId, URI uploadUrl, Instant expiresAt) {
        static UploadResponse of(Upload upload) {
            return new UploadResponse(
                    upload.file().getId(),
                    Objects.requireNonNull(upload.url()),
                    upload.file().getUploadExpiresAt());
        }
    }

    record ChangeRequest(@NotNull UUID fileId) {}
}
