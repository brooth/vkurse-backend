/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.platform.school.web;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import space.vkurse.core.security.GetSession;
import space.vkurse.core.security.UserSession;
import space.vkurse.core.web.Paging;
import space.vkurse.platform.school.application.SchoolFileService;
import space.vkurse.platform.storage.domain.Download;

/** A school's file library, for its staff. */
@RestController
@RequestMapping("/schools/{schoolId}/files")
class SchoolFileController {
    private final SchoolFileService files;

    SchoolFileController(SchoolFileService files) {
        this.files = files;
    }

    /** Step 1: asks for a link to upload a file with. */
    @PostMapping
    UploadResponse requestUpload(
            @GetSession UserSession session, @PathVariable UUID schoolId, @Valid @RequestBody UploadRequest data) {
        return UploadResponse.of(
                files.requestUpload(session.userId(), schoolId, data.name(), data.contentType(), data.size()));
    }

    /** Step 2, after the file was sent to storage: puts it into the library. Only the uploader may. */
    @PostMapping("/{fileId}/confirm")
    FileResponse confirm(@GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID fileId) {
        return FileResponse.of(files.confirm(session.userId(), schoolId, fileId));
    }

    /** The confirmed files, newest first. {@code $search} looks in their names. */
    @GetMapping
    List<FileResponse> list(
            @GetSession UserSession session, @PathVariable UUID schoolId, @RequestParam Map<String, String> params) {
        return files.list(session.userId(), schoolId, Paging.parse(params)).stream()
                .map(FileResponse::of)
                .toList();
    }

    /** A signed link that downloads the file. It is returned in the body, never as a redirect. */
    @PostMapping("/{fileId}/download")
    DownloadResponse download(@GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID fileId) {
        return DownloadResponse.of(files.download(session.userId(), schoolId, fileId));
    }

    @DeleteMapping("/{fileId}")
    ResponseEntity<Void> delete(
            @GetSession UserSession session, @PathVariable UUID schoolId, @PathVariable UUID fileId) {
        files.delete(session.userId(), schoolId, fileId);
        return ResponseEntity.noContent().build();
    }

    record DownloadResponse(URI url, @Nullable Instant expiresAt) {
        static DownloadResponse of(Download download) {
            return new DownloadResponse(download.url(), download.expiresAt());
        }
    }
}
