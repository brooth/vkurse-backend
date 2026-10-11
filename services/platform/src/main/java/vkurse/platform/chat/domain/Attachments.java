/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.List;
import java.util.UUID;

/** The stored files attached to a message. Stored as one JSON value. */
public record Attachments(List<UUID> fileIds) {
    public static final Attachments NONE = new Attachments(List.of());

    public Attachments {
        fileIds = List.copyOf(fileIds);
    }
}
