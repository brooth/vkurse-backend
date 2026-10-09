/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.user.application;

import java.util.Map;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import vkurse.core.notification.Content;
import vkurse.core.notification.Message;
import vkurse.core.notification.NotificationService;
import vkurse.core.notification.Target;
import vkurse.platform.user.domain.UserRegistered;
import vkurse.platform.user.domain.UserRepository;

/** Greets a new user by email. Runs after the sign-up is committed; the email itself is sent by the notification job. */
@Component
class WelcomeListener {
    private final UserRepository users;
    private final NotificationService notifications;

    WelcomeListener(UserRepository users, NotificationService notifications) {
        this.users = users;
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(UserRegistered event) {
        users.findById(event.userId())
                .ifPresent(user -> notifications.submit(Message.of(
                        Content.template("welcome", user.getLocale(), Map.of("name", user.getName())),
                        Target.email(user.getEmail().value()))));
    }
}
