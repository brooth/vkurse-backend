/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.notification;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivers submitted notifications, and removes old ones. The retry rules are in {@link Notification}. */
class NotificationJobs {
    private static final Logger log = LoggerFactory.getLogger(NotificationJobs.class);

    /** How many notifications one run delivers. Bounded, so a run ends well before its lock does. */
    private static final int BATCH_SIZE = 20;

    private final NotificationRepository notifications;
    private final NotificationService service;
    private final Clock clock;
    private final TransactionTemplate transaction;

    NotificationJobs(
            NotificationRepository notifications,
            NotificationService service,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.notifications = notifications;
        this.service = service;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Delivers the notifications that are due. Each is sent outside a transaction and its outcome saved in a short
     * one, so no database connection is held while talking to the mail server. If the node dies in between, the
     * notification is sent again: delivery is at-least-once.
     */
    @Scheduled(cron = "*/10 * * * * *")
    @SchedulerLock(name = "notification-dispatch", lockAtMostFor = "PT10M")
    public void dispatch() {
        List<Notification> due = Objects.requireNonNull(
                transaction.execute(status -> notifications.findDue(clock.instant(), BATCH_SIZE)));
        due.forEach(this::deliver);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "notification-cleanup")
    public void deleteOld() {
        var deleted = transaction.execute(
                status -> notifications.deleteFinishedBefore(clock.instant().minus(Notification.RETENTION)));
        log.info("Notification cleanup deleted {} rows", deleted);
    }

    private void deliver(Notification due) {
        @Nullable Delivery delivery = null;
        @Nullable String error = null;
        try {
            delivery = service.send(due.getMessage());
        } catch (RuntimeException e) {
            // NotificationService logged each failed target.
            error = e.getMessage();
        }
        var delivered = delivery;
        var failure = error;
        transaction.executeWithoutResult(status -> notifications
                .findByIdForUpdate(due.getId())
                .ifPresent(notification -> {
                    if (delivered != null) {
                        notification.delivered(delivered, clock.instant());
                        log.info(
                                "Notification {} delivered by {} on attempt {}/{}",
                                notification.getId(),
                                delivered.channel(),
                                notification.getAttempts(),
                                Notification.MAX_ATTEMPTS);
                    } else {
                        notification.failed(failure, clock.instant());
                        if (notification.getStatus() == Notification.Status.FAILED) {
                            log.error(
                                    "Notification {} gave up after {} attempts, the last error is {}",
                                    notification.getId(),
                                    notification.getAttempts(),
                                    failure);
                        } else {
                            log.warn(
                                    "Notification {} failed on attempt {}/{}, the next one is at {}, the error is {}",
                                    notification.getId(),
                                    notification.getAttempts(),
                                    Notification.MAX_ATTEMPTS,
                                    notification.getNextAttemptAt(),
                                    failure);
                        }
                    }
                    notifications.save(notification);
                }));
    }
}
