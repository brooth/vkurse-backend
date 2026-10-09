/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.platform.school.application.SchoolLogoService;

/** A school's logo, for those who may edit the school. */
@RestController
@RequestMapping("/schools/{schoolId}/logo")
class SchoolLogoController {
    private final SchoolLogoService logos;

    SchoolLogoController(SchoolLogoService logos) {
        this.logos = logos;
    }

    /** Step 1: asks for a link to upload an image with. */
    @PostMapping("/upload")
    UploadResponse requestUpload(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                logos.requestUpload(session.userId(), schoolId, data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the image was sent to storage: makes it the logo. */
    @PostMapping
    SchoolResponse change(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody ChangeRequest data) {
        return SchoolResponse.of(logos.change(session.userId(), schoolId, data.fileId()));
    }

    @DeleteMapping
    ResponseEntity<Void> remove(@GetSession UserSession session, @PathVariable UUID schoolId) {
        logos.remove(session.userId(), schoolId);
        return ResponseEntity.noContent().build();
    }

    record ChangeRequest(@NotNull UUID fileId) {}
}
