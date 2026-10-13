/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import vkurse.core.notification.Target.EmailTarget;
import vkurse.core.notification.Target.TelegramTarget;

/**
 * Sends notifications. Two ways, by what a failure should mean:
 *
 * <ul>
 *   <li>{@link #send} delivers now and nothing is stored: the caller learns at once whether it worked, and a failure
 *       is the caller's to handle. For what the user is waiting for (a sign-in code).
 *   <li>{@link #submit} stores the message, and a job delivers it within seconds, retrying for a while if it fails.
 *       For everything else.
 * </ul>
 */
public class NotificationService {
    /** The logger the text of a sent message goes to, at DEBUG. Keep it off in production: a text may hold a code. */
    public static final String BODY_LOGGER = "vkurse.notification.body";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Logger bodies = LoggerFactory.getLogger(BODY_LOGGER);

    private final NotificationRepository notifications;
    private final TemplateRenderer renderer;
    private final SmtpEmailSender email;
    private final TelegramBotSender telegram;
    private final Clock clock;

    NotificationService(
            NotificationRepository notifications,
            TemplateRenderer renderer,
            SmtpEmailSender email,
            TelegramBotSender telegram,
            Clock clock) {
        this.notifications = notifications;
        this.renderer = renderer;
        this.email = email;
        this.telegram = telegram;
        this.clock = clock;
    }

    /**
     * Delivers the message now: tries its attempts in order, and each attempt's targets in order, until one takes it.
     * Throws {@link DeliveryFailedException} if none does. It talks to the mail server or Telegram, so don't call it
     * inside a database transaction.
     */
    public Delivery send(Message message) {
        @Nullable RuntimeException failure = null;
        for (var attempt : message.attempts()) {
            Rendered rendered;
            try {
                rendered = renderer.render(attempt.content());
            } catch (RuntimeException e) {
                log.error("Failed to render notification {}", attempt.content().describe(), e);
                failure = e;
                continue;
            }
            for (var target : attempt.targets()) {
                try {
                    var messageId = switch (target) {
                        case EmailTarget to -> email.send(to, rendered);
                        case TelegramTarget to -> telegram.send(to, rendered);
                    };
                    log.info(
                            "Sent {} by {} to {}{}, subject \"{}\", message id {}",
                            attempt.content().describe(),
                            target.channel(),
                            target.recipient(),
                            target instanceof EmailTarget ? " via " + email.server() : "",
                            rendered.subject(),
                            messageId);
                    bodies.debug(
                            "Text of {} to {}: {}", attempt.content().describe(), target.recipient(), rendered.text());
                    return new Delivery(target.channel(), messageId);
                } catch (RuntimeException e) {
                    log.warn(
                            "Failed to send {} by {} to {}",
                            attempt.content().describe(),
                            target.channel(),
                            target.recipient(),
                            e);
                    failure = e;
                }
            }
        }
        // A message has at least one attempt with at least one target, so something failed.
        throw new DeliveryFailedException(Objects.requireNonNull(failure));
    }

    /**
     * Stores the message for delivery and returns the notification's id. Inside a transaction it is saved with the
     * caller's changes, so it is sent only if they are committed. The message is stored as it is: never submit a
     * secret (a sign-in code, a token).
     */
    @Transactional
    public UUID submit(Message message) {
        var id = notifications
                .save(Notification.submit(message, clock.instant()))
                .getId();
        log.info("Submitted notification {}: {}", id, message.describe());
        return id;
    }
}
