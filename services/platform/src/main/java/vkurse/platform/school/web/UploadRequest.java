/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.school.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import vkurse.platform.storage.domain.StoredFile;

/** The file a client is about to upload: its name, content type and size in bytes. */
record UploadRequest(
        @NotBlank @Size(max = StoredFile.MAX_NAME_LENGTH) String name,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull @Positive Long size) {}
