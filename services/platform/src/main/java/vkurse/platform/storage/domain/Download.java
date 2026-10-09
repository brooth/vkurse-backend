/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.storage.domain;

import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A link a file can be read with. A signed link works until {@code expiresAt}; the link of a public file has no end
 * ({@code null}).
 */
public record Download(URI url, @Nullable Instant expiresAt) {}
