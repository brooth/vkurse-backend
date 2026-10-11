/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.chat.domain;

import org.springframework.http.HttpStatus;
import vkurse.core.error.Errors.ClientException;

public final class ChatErrors {

    private ChatErrors() {}

    /** 429 {@code chat/too-many-messages}: the person wrote to the chat too often; they may write again in a minute. */
    public static class TooManyMessagesException extends ClientException {
        public TooManyMessagesException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "chat/too-many-messages", "Too many messages, wait a minute");
        }
    }

    /** 429 {@code chat/too-many-streams}: the person has as many open streams as one may have. */
    public static class TooManyStreamsException extends ClientException {
        public TooManyStreamsException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "chat/too-many-streams", "Too many open chat streams");
        }
    }

    /** 409 {@code chat/too-many-pins}: the chat has as many pinned banners as it may have. */
    public static class TooManyPinsException extends ClientException {
        public TooManyPinsException() {
            super(
                    HttpStatus.CONFLICT,
                    "chat/too-many-pins",
                    "The chat has " + Message.MAX_PINNED + " pinned banners already, unpin one first");
        }
    }

    /** 409 {@code chat/message-deleted}: a deleted message can't be changed, voted on, pinned or replied to. */
    public static class MessageDeletedException extends ClientException {
        public MessageDeletedException() {
            super(HttpStatus.CONFLICT, "chat/message-deleted", "This message was deleted");
        }
    }
}
