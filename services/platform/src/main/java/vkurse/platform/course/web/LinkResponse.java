/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.course.web;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import vkurse.platform.storage.domain.Download;

/** A signed link to the file a block shows. It stops working at {@code expiresAt}: ask for the links again then. */
public record LinkResponse(URI url, @Nullable Instant expiresAt) {

    /** By block id. A block whose file is gone has no entry. */
    public static Map<UUID, LinkResponse> of(Map<UUID, Download> links) {
        return links.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        link -> new LinkResponse(
                                link.getValue().url(), link.getValue().expiresAt())));
    }
}
