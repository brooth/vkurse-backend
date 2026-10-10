/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * An upload a client may now make, before {@link StoredFile#getUploadExpiresAt()}, and then confirm. Exactly one way is
 * given: {@code url}, to {@code PUT} the whole file to with its content type, or {@code resumable}, for a big file.
 */
public record Upload(
        StoredFile file, @Nullable URI url, @Nullable ResumableUpload resumable) {}
