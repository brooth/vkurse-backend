/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.net.URI;

/**
 * An upload a client may now make: {@code PUT} the file to {@code url} with its content type, before
 * {@link StoredFile#getUploadExpiresAt()}, then confirm it.
 */
public record Upload(StoredFile file, URI url) {}
