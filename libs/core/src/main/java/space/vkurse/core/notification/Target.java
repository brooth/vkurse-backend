/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Where to deliver a notification: a channel and the recipient's address in it. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Target.EmailTarget.class, name = "email"),
    @JsonSubTypes.Type(value = Target.TelegramTarget.class, name = "telegram")
})
public sealed interface Target {

    Channel channel();

    static Target email(String address) {
        return new EmailTarget(address);
    }

    static Target telegram(String chatId) {
        return new TelegramTarget(chatId);
    }

    /** @param address the recipient's email address */
    record EmailTarget(String address) implements Target {
        public EmailTarget {
            if (address.isBlank()) {
                throw new IllegalArgumentException("An email target needs an address");
            }
        }

        @Override
        public Channel channel() {
            return Channel.EMAIL;
        }
    }

    /** @param chatId the Telegram chat the bot writes to: a user who started the bot, or a group it was added to */
    record TelegramTarget(String chatId) implements Target {
        public TelegramTarget {
            if (chatId.isBlank()) {
                throw new IllegalArgumentException("A Telegram target needs a chat id");
            }
        }

        @Override
        public Channel channel() {
            return Channel.TELEGRAM;
        }
    }
}
