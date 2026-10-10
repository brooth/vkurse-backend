/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import vkurse.platform.storage.domain.ResumableUpload;
import vkurse.platform.storage.domain.Upload;

/**
 * An upload the client may make, before {@code expiresAt}. With {@code uploadUrl}: {@code PUT} the raw file to it, with
 * the declared content type. With {@code resumable} (a video): upload with the tus protocol to its {@code endpoint},
 * sending {@code signature} in the {@code x-signature} header of every request, and {@code bucketName},
 * {@code objectName} and {@code contentType} as the upload's metadata.
 */
public record UploadResponse(
        FileResponse file,
        @Nullable URI uploadUrl,
        @Nullable ResumableUpload resumable,
        Instant expiresAt) {

    public static UploadResponse of(Upload upload) {
        return new UploadResponse(
                FileResponse.of(upload.file()),
                upload.url(),
                upload.resumable(),
                upload.file().getUploadExpiresAt());
    }
}
