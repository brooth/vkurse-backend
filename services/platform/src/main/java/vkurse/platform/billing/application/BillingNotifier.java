/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.platform.billing.application;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;
import vkurse.core.notification.Content;
import vkurse.core.notification.Message;
import vkurse.core.notification.NotificationService;
import vkurse.core.notification.Target;
import vkurse.platform.PlatformProperties;
import vkurse.platform.billing.domain.BillingAccount;
import vkurse.platform.school.application.SchoolService;
import vkurse.platform.user.application.UserService;

/** The emails of this module, to a school's owner. Each is stored with the caller's changes and delivered by a job. */
@Component
class BillingNotifier {
    static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final NotificationService notifications;
    private final SchoolService schools;
    private final UserService users;
    private final PlatformProperties properties;

    BillingNotifier(
            NotificationService notifications,
            SchoolService schools,
            UserService users,
            PlatformProperties properties) {
        this.notifications = notifications;
        this.schools = schools;
        this.users = users;
        this.properties = properties;
    }

    /** Tells the owner of the account's school about its state. Nothing is sent if the school or its owner is gone. */
    void tell(BillingAccount account, BillingAccount.Notice notice) {
        var school = schools.find(account.getSchoolId()).orElse(null);
        var owner = schools.findOwnerId(account.getSchoolId())
                .flatMap(users::findById)
                .orElse(null);
        if (school == null || owner == null) {
            return;
        }
        var suspendsAt = account.suspendsAt();
        var template = switch (notice) {
            case LOW_BALANCE -> "billing-low-balance";
            case DEBT -> "billing-debt";
            case SUSPENDED -> "billing-suspended";
        };
        notifications.submit(Message.of(
                Content.template(
                        template,
                        owner.getLocale(),
                        Map.of(
                                "name", owner.getName(),
                                "schoolName", school.getName(),
                                "balance", account.getBalanceAmount().value().toPlainString(),
                                "suspendsAt", suspendsAt == null ? "" : DAY.format(suspendsAt.atZone(MOSCOW)),
                                "link", properties.webUrl().toString())),
                Target.email(owner.getEmail().value())));
    }
}
