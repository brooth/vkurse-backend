/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import java.util.UUID;

/**
 * Something that happened to a message of a chat, as the chat's open streams hear of it. It is not a domain event of
 * the service: it goes to every node, to be passed on to the people watching the chat.
 */
public record ChatEvent(Kind kind, UUID chatId, UUID messageId) {

    public enum Kind {
        /** A message was written. */
        CREATED("message.created"),
        /** A message was edited, voted on, pinned or unpinned. */
        UPDATED("message.updated"),
        /** A message was deleted. */
        DELETED("message.deleted");

        private final String eventName;

        Kind(String eventName) {
            this.eventName = eventName;
        }

        /** The name of the event in a stream. */
        public String eventName() {
            return eventName;
        }
    }
}
