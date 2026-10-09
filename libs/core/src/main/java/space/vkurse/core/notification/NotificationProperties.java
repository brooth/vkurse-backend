/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Notification settings ({@code vkurse.notification.*}). A missing value stops startup. The mail server itself is
 * {@code spring.mail.*}.
 *
 * @param mailFrom sender address of our emails, e.g. {@code VKurse <no-reply@vkurse.ru>}
 * @param telegram the Telegram bot that sends our messages
 */
@Validated
@ConfigurationProperties("vkurse.notification")
public record NotificationProperties(
        @NotBlank String mailFrom, @Valid @NotNull Telegram telegram) {

    /** @param botToken Bot API token of the bot (from BotFather) */
    public record Telegram(@NotBlank String botToken) {}
}
