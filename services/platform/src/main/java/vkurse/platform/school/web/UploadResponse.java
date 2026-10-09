/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import java.net.URI;
import java.time.Instant;
import vkurse.platform.storage.domain.Upload;

/**
 * An upload the client may make: {@code PUT} the raw file to {@code uploadUrl}, with the declared content type, before
 * {@code expiresAt}.
 */
public record UploadResponse(FileResponse file, URI uploadUrl, Instant expiresAt) {

    public static UploadResponse of(Upload upload) {
        return new UploadResponse(
                FileResponse.of(upload.file()), upload.url(), upload.file().getUploadExpiresAt());
    }
}
