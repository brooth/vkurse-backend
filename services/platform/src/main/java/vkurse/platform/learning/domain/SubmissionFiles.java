/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.learning.domain;

import java.util.List;
import java.util.UUID;

/** The stored files handed in with a homework answer. Stored as one JSON value. */
public record SubmissionFiles(List<UUID> fileIds) {
    public static final SubmissionFiles NONE = new SubmissionFiles(List.of());

    public SubmissionFiles {
        fileIds = List.copyOf(fileIds);
    }
}
