/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.user.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkurse.core.security.GetSession;
import vkurse.core.security.UserSession;
import vkurse.platform.storage.domain.StoredFile;
import vkurse.platform.storage.domain.Upload;
import vkurse.platform.user.application.UserPictureService;

/** The caller's own picture. */
@RestController
@RequestMapping("/users/me/picture")
class UserPictureController {
    private final UserPictureService pictures;

    UserPictureController(UserPictureService pictures) {
        this.pictures = pictures;
    }

    /** Step 1: asks for a link to upload an image with. */
    @PostMapping("/upload")
    UploadResponse requestUpload(@GetSession UserSession session, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                pictures.requestUpload(session.userId(), data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the image was sent to storage: makes it the picture. */
    @PostMapping
    UserResponse change(@GetSession UserSession session, @Valid @RequestBody ChangeRequest data) {
        return UserResponse.of(pictures.change(session.userId(), data.fileId()));
    }

    /** Removes the picture, an uploaded one or the one a sign-in provider gave. */
    @DeleteMapping
    ResponseEntity<Void> remove(@GetSession UserSession session) {
        pictures.remove(session.userId());
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
                    upload.file().getId(), upload.url(), upload.file().getUploadExpiresAt());
        }
    }

    record ChangeRequest(@NotNull UUID fileId) {}
}
