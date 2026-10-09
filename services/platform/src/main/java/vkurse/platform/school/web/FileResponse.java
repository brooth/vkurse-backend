/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.time.Instant;
import java.util.UUID;
import vkurse.platform.storage.domain.StoredFile;

/** A stored file as the API shows it. It has no link: a download link is asked for when it is needed. */
public record FileResponse(
        UUID id,
        Instant createdAt,
        String name,
        String contentType,
        long size,
        StoredFile.Status status,
        UUID uploadedBy) {

    public static FileResponse of(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getCreatedAt(),
                file.getName(),
                file.getContentType(),
                file.getSize(),
                file.getStatus(),
                file.getUploadedBy());
    }
}
